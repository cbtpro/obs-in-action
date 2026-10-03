import { useCallback, useRef, useState } from 'react';
import SparkMD5 from 'spark-md5';
import { Button, Progress, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { abortMultipart, completeMultipart, initMultipart, uploadMultipartPart } from '../api/multipart';
import { MultipartPartItem } from '../types/multipart';
import { FileUploadResponse } from '../types/file';
import { useDeviceEnv } from '../hooks/useDeviceEnv';
import { ACCEPT_GENERAL, MAX_FILE_SIZE, checkFileSize } from '../utils/upload';

/** 并发上传的分片数：过大易触发厂商限流，过小吞吐上不去 */
const CONCURRENCY = 3;
/** 单分片失败重试次数（指数退避） */
const PART_MAX_RETRY = 3;

/** 小于该阈值走单文件上传更划算，分片的开销（init/complete 往返）不值得 */
const CHUNK_THRESHOLD = 8 * 1024 * 1024;

interface ChunkUploaderProps {
  onUploaded: (r: FileUploadResponse) => void;
}

/** 任务控制：工作循环在分片间隙检查暂停/取消标记 */
interface TaskControls {
  isPaused: () => boolean;
  isCancelled: () => boolean;
}

type ChunkStatus = 'uploading' | 'paused' | 'success' | 'error';

interface ChunkTaskState {
  uid: string;
  name: string;
  progress: number;
  status: ChunkStatus;
  error?: string;
  /** 保留文件引用，用于失败重试与暂停续传 */
  file?: File;
  sessionId?: number;
}

/**
 * 分片上传业务组件。
 * 与 FileUploader 并存互补：小文件走单文件上传，大文件走分片 + 断点续传。
 * 断点续传三要素：
 * - 指纹：文件名+大小+修改时间哈希，页面刷新后重选同一文件可找回会话；
 * - 云端权威：init 返回云端已确认分片，客户端只补缺失部分；
 * - 幂等：分片号重复上传云端覆盖，complete 重复到达服务端返回既有结果。
 */
export default function ChunkUploader({ onUploaded }: ChunkUploaderProps) {
  const [tasks, setTasks] = useState<ChunkTaskState[]>([]);
  const { isIOS } = useDeviceEnv();
  const pausedRef = useRef<Set<string>>(new Set());
  const cancelledRef = useRef<Set<string>>(new Set());

  const updateTask = useCallback((uid: string, patch: Partial<ChunkTaskState>) => {
    setTasks((prev) => prev.map((t) => (t.uid === uid ? { ...t, ...patch } : t)));
  }, []);

  /** 分片上传主流程：init → 并发补缺失分片 → complete */
  const runChunkUpload = useCallback(
    async (file: File, uid: string, existingTask?: ChunkTaskState) => {
      try {
        const [fingerprint, contentHash] = await Promise.all([
          fileFingerprint(file),
          fileContentHash(file),
        ]);
        const init = await initMultipart({
          name: file.name,
          size: file.size,
          contentType: file.type,
          fingerprint,
          contentHash,
        });

        if (cancelledRef.current.has(uid)) return;
        if (init.instantUpload) {
          if (!init.file) throw new Error('秒传响应缺少文件信息');
          updateTask(uid, { progress: 100, status: 'success' });
          onUploaded(init.file);
          return;
        }
        if (init.sessionId == null) throw new Error('分片初始化响应缺少会话标识');
        if (existingTask) {
          updateTask(uid, { sessionId: init.sessionId, status: 'uploading', error: undefined });
        }

        await uploadMissingParts(file, uid, init, updateTask, {
          isPaused: () => pausedRef.current.has(uid),
          isCancelled: () => cancelledRef.current.has(uid),
        });

        if (cancelledRef.current.has(uid)) return;
        if (pausedRef.current.has(uid)) {
          updateTask(uid, { status: 'paused' });
          return;
        }

        const result = await completeMultipart(init.sessionId);
        updateTask(uid, { progress: 100, status: 'success' });
        onUploaded(result);
      } catch (e) {
        if (cancelledRef.current.has(uid)) return;
        if (pausedRef.current.has(uid)) {
          updateTask(uid, { status: 'paused' });
          return;
        }
        const msg = e instanceof Error ? e.message : '上传失败';
        updateTask(uid, { status: 'error', error: msg });
      }
    },
    [onUploaded, updateTask],
  );

  const handleUpload = useCallback(
    (file: File, existing?: ChunkTaskState) => {
      const uid = existing?.uid ?? `${file.name}-${Date.now()}`;
      if (existing) {
        pausedRef.current.delete(uid);
        updateTask(uid, { status: 'uploading', progress: 0, error: undefined });
      } else {
        setTasks((prev) => [...prev, { uid, name: file.name, progress: 0, status: 'uploading', file }]);
      }
      void runChunkUpload(file, uid, existing);
    },
    [runChunkUpload, updateTask],
  );

  const handlePause = useCallback((task: ChunkTaskState) => {
    pausedRef.current.add(task.uid);
    // 不改状态：等工作循环自然停在分片间隙后由 runChunkUpload 落 paused
  }, []);

  const handleResume = useCallback(
    (task: ChunkTaskState) => {
      if (task.file) handleUpload(task.file, task);
    },
    [handleUpload],
  );

  const handleCancel = useCallback(
    async (task: ChunkTaskState) => {
      cancelledRef.current.add(task.uid);
      if (task.sessionId) {
        try {
          await abortMultipart(task.sessionId);
        } catch {
          /* 幂等终止，失败不影响本地移除 */
        }
      }
      setTasks((prev) => prev.filter((x) => x.uid !== task.uid));
      cancelledRef.current.delete(task.uid);
      pausedRef.current.delete(task.uid);
    },
    [],
  );

  const uploadProps: UploadProps = {
    accept: ACCEPT_GENERAL,
    multiple: true,
    showUploadList: false,
    beforeUpload: (file) => {
      const sizeError = checkFileSize(file as File, MAX_FILE_SIZE, '文件');
      if (sizeError) {
        message.error(sizeError);
        return Upload.LIST_IGNORE;
      }
      if (file.size === 0) {
        message.error('不能上传空文件');
        return Upload.LIST_IGNORE;
      }
      if (file.size < CHUNK_THRESHOLD) {
        message.info('小文件建议使用上方普通上传');
      }
      handleUpload(file as File);
      return false;
    },
  };

  return (
    <section className="uploader">
      <Upload.Dragger {...uploadProps} disabled={isIOS && false}>
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽大文件到此区域分片上传</p>
        <p className="ant-upload-hint">
          超过 8MB 的大文件自动分片，支持断点续传（刷新页面后重选同一文件可继续），单文件最大 50MB
        </p>
      </Upload.Dragger>

      {tasks.length > 0 && (
        <ul className="upload-tasks">
          {tasks.map((t) => (
            <li key={t.uid} className={`task task-${t.status}`}>
              <span className="task-name">{t.name}</span>
              {(t.status === 'uploading' || t.status === 'paused') && (
                <Progress percent={t.progress} size="small" className="task-progress" status={t.status === 'paused' ? 'normal' : 'active'} />
              )}
              {t.status === 'uploading' && (
                <Button size="small" type="link" onClick={() => handlePause(t)}>
                  暂停
                </Button>
              )}
              {t.status === 'paused' && (
                <Button size="small" type="link" onClick={() => handleResume(t)}>
                  继续
                </Button>
              )}
              {t.status === 'error' && (
                <span className="task-error">
                  {t.error}
                  {t.file && (
                    <Button size="small" type="link" onClick={() => handleUpload(t.file as File, t)}>
                      重试
                    </Button>
                  )}
                </span>
              )}
              {t.status !== 'success' && t.status !== 'error' && (
                <Button size="small" type="link" onClick={() => void handleCancel(t)}>
                  取消
                </Button>
              )}
              {t.status === 'success' && <span className="task-done">已完成</span>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * 并发补传缺失分片。
 * 进度 = (云端已确认字节 + 本次完成字节 + 在途已发出字节) / 总字节。
 */
async function uploadMissingParts(
  file: File,
  uid: string,
  init: { sessionId?: number; chunkSize: number; uploadedParts: MultipartPartItem[] },
  updateTask: (uid: string, patch: Partial<ChunkTaskState>) => void,
  controls: TaskControls,
): Promise<void> {
  const { sessionId, chunkSize, uploadedParts } = init;
  if (sessionId == null) throw new Error('分片初始化响应缺少会话标识');
  const totalParts = Math.max(1, Math.ceil(file.size / chunkSize));
  const uploaded = new Set(uploadedParts.map((p) => p.partNumber));

  const missing: number[] = [];
  for (let i = 1; i <= totalParts; i++) {
    if (!uploaded.has(i)) missing.push(i);
  }

  let baseBytes = uploadedParts.reduce((sum, p) => sum + p.size, 0);
  let doneBytes = 0;
  const inflight = new Map<number, number>();
  let lastPercent = -1;

  const reportProgress = () => {
    let inflightBytes = 0;
    inflight.forEach((v) => (inflightBytes += v));
    const percent = Math.min(
      99,
      Math.round(((baseBytes + doneBytes + inflightBytes) / file.size) * 100),
    );
    if (percent !== lastPercent) {
      lastPercent = percent;
      updateTask(uid, { progress: percent });
    }
  };
  reportProgress();

  if (missing.length === 0) return;

  let cursor = 0;
  let failure: unknown = null;

  const worker = async (): Promise<void> => {
    while (true) {
      if (failure || controls.isPaused() || controls.isCancelled()) return;
      const idx = cursor++;
      if (idx >= missing.length) return;

      const partNumber = missing[idx];
      const start = (partNumber - 1) * chunkSize;
      const blob = file.slice(start, Math.min(start + chunkSize, file.size));
      const contentMd5 = await blobContentMd5(blob);
      inflight.set(partNumber, 0);

      for (let attempt = 1; attempt <= PART_MAX_RETRY; attempt++) {
        try {
          await uploadMultipartPart(sessionId, partNumber, blob, contentMd5, (loaded) => {
            inflight.set(partNumber, loaded);
            reportProgress();
          });
          break;
        } catch (e) {
          if (controls.isPaused() || controls.isCancelled()) return;
          if (attempt === PART_MAX_RETRY) {
            failure = e;
            return;
          }
          await sleep(500 * 2 ** (attempt - 1));
        }
      }

      inflight.delete(partNumber);
      doneBytes += blob.size;
      reportProgress();
    }
  };

  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, missing.length) }, () => worker()));
  if (failure) {
    throw failure;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 计算文件指纹：文件名+大小+修改时间的 SHA-256（128 字符内）。
 * 同一文件重选（含刷新页面后）指纹不变，用于服务端找回会话。
 * 非安全上下文（无 HTTPS）下降级为简易哈希，仅本地调试可用。
 */
async function fileFingerprint(file: File): Promise<string> {
  const raw = `${file.name}:${file.size}:${file.lastModified}`;
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw));
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  let h = 0;
  for (let i = 0; i < raw.length; i++) {
    h = (h * 31 + raw.charCodeAt(i)) | 0;
  }
  return `fallback-${(h >>> 0).toString(16)}`;
}

/** 完整文件 SHA-256。它是秒传身份，不使用文件名或修改时间。 */
async function fileContentHash(file: File): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前页面不支持安全哈希，请使用 HTTPS 或 localhost');
  }
  const digest = await globalThis.crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/** Content-MD5 使用 Base64 编码，供服务端和对象存储逐片校验。 */
async function blobContentMd5(blob: Blob): Promise<string> {
  const binary = SparkMD5.ArrayBuffer.hash(await blob.arrayBuffer(), true);
  return btoa(binary);
}

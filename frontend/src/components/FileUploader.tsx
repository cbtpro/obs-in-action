import { useCallback, useState } from 'react';
import { Button, Progress, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { uploadFile } from '../api/files';
import { FileUploadResponse } from '../types/file';
import { useDeviceEnv } from '../hooks/useDeviceEnv';
import { ACCEPT_GENERAL, MAX_FILE_SIZE, checkFileSize } from '../utils/upload';

interface FileUploaderProps {
  onUploaded: (r: FileUploadResponse) => void;
}

interface TaskState {
  uid: string;
  name: string;
  progress: number;
  status: 'uploading' | 'success' | 'error';
  error?: string;
  /** 保留文件引用，用于失败重试 */
  file?: File;
}

/**
 * 文件上传业务组件。
 * 使用 antd Upload.Dragger（拖拽 + 点击），iOS Safari 不支持 drop 自动降级为点击。
 * 上传前做大小预校验，失败任务可一键重试。
 */
export default function FileUploader({ onUploaded }: FileUploaderProps) {
  const [tasks, setTasks] = useState<TaskState[]>([]);
  const { isIOS } = useDeviceEnv();

  const updateTask = useCallback((uid: string, patch: Partial<TaskState>) => {
    setTasks((prev) => prev.map((t) => (t.uid === uid ? { ...t, ...patch } : t)));
  }, []);

  const handleUpload = useCallback(
    async (file: File, existingUid?: string) => {
      // 重试复用原 task，不重复追加
      const uid = existingUid ?? `${file.name}-${Date.now()}`;
      if (existingUid) {
        updateTask(uid, { status: 'uploading', progress: 0, error: undefined });
      } else {
        setTasks((prev) => [...prev, { uid, name: file.name, progress: 0, status: 'uploading', file }]);
      }
      try {
        const r = await uploadFile(file, (p) => updateTask(uid, { progress: p }));
        updateTask(uid, { progress: 100, status: 'success' });
        onUploaded(r);
      } catch (e) {
        const msg = e instanceof Error ? e.message : '上传失败';
        updateTask(uid, { status: 'error', error: msg });
      }
    },
    [onUploaded, updateTask],
  );

  const uploadProps: UploadProps = {
    accept: ACCEPT_GENERAL,
    multiple: true,
    showUploadList: false,
    beforeUpload: (file) => {
      // 大小预校验：超限直接拦截，不发起请求浪费流量
      const sizeError = checkFileSize(file, MAX_FILE_SIZE, '文件');
      if (sizeError) {
        message.error(sizeError);
        return Upload.LIST_IGNORE;
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
        <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
        <p className="ant-upload-hint">支持图片（jpg/png/gif/webp）、PDF、Word、Excel、ZIP、TXT，单文件最大 50MB</p>
      </Upload.Dragger>

      {tasks.length > 0 && (
        <ul className="upload-tasks">
          {tasks.map((t) => (
            <li key={t.uid} className={`task task-${t.status}`}>
              <span className="task-name">{t.name}</span>
              {t.status === 'uploading' && <Progress percent={t.progress} size="small" className="task-progress" />}
              {t.status === 'error' && (
                <span className="task-error">
                  {t.error}
                  {t.file && (
                    <Button size="small" type="link" onClick={() => handleUpload(t.file as File, t.uid)}>
                      重试
                    </Button>
                  )}
                  <Button
                    size="small"
                    type="link"
                    onClick={() => {
                      setTasks((prev) => prev.filter((x) => x.uid !== t.uid));
                    }}
                  >
                    移除
                  </Button>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

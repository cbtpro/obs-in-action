import http from './client';
import type { AxiosProgressEvent } from 'axios';
import { FileItem, FileUploadResponse } from '../types/file';

/**
 * 文件相关接口封装。
 * 所有方法返回 CancellablePromise，调用方可在 useEffect cleanup 中调 cancel()。
 */

/**
 * 上传文件，带进度回调。
 */
export function uploadFile(
  file: File,
  onProgress?: (percent: number) => void,
) {
  const form = new FormData();
  form.append('file', file);
  return http.post<FileUploadResponse>('/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e: AxiosProgressEvent) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded * 100) / e.total));
      }
    },
  });
}

/**
 * 列出已上传文件。
 */
export function listFiles(limit = 100) {
  return http.get<FileItem[]>('/files', { params: { limit } });
}

/**
 * 删除文件。
 */
export function deleteFile(key: string) {
  return http.delete<void>(`/files/${encodeURIComponent(key)}`);
}

/**
 * 获取回显/下载代理地址（同源代理，无需 token）。
 */
export function getFileProxyUrl(key: string): string {
  return `/api/files/${encodeURIComponent(key)}`;
}

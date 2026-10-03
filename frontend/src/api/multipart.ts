import http from './client';
import type { AxiosProgressEvent } from 'axios';
import { FileUploadResponse } from '../types/file';
import { MultipartInitRequest, MultipartInitResponse, MultipartPartResponse } from '../types/multipart';

/**
 * 分片上传接口封装。
 * 所有方法返回 CancellablePromise，调用方可在 useEffect cleanup 中调 cancel()。
 */

/**
 * 发起分片上传（或按指纹续传）。
 */
export function initMultipart(request: MultipartInitRequest) {
  return http.post<MultipartInitResponse>('/multipart/init', request);
}

/**
 * 上传单个分片，返回云端 etag。
 * 同一分片号重复上传云端直接覆盖（重试安全）。
 */
export function uploadMultipartPart(
  sessionId: number,
  partNumber: number,
  data: Blob,
  contentMd5: string,
  onProgress?: (loaded: number) => void,
) {
  return http.put<MultipartPartResponse>(`/multipart/${sessionId}/parts/${partNumber}`, data, {
    headers: {
      'Content-Type': 'application/octet-stream',
      'Content-MD5': contentMd5,
    },
    onUploadProgress: (e: AxiosProgressEvent) => {
      if (onProgress && e.total) {
        onProgress(e.loaded);
      }
    },
  });
}

/**
 * 完成分片上传：云端拼接对象并落文件元数据。
 */
export function completeMultipart(sessionId: number) {
  return http.post<FileUploadResponse>(`/multipart/${sessionId}/complete`);
}

/**
 * 终止分片上传：云端删除已传分片，会话作废（幂等）。
 */
export function abortMultipart(sessionId: number) {
  return http.delete<void>(`/multipart/${sessionId}`);
}

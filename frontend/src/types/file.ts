/**
 * 文件上传成功响应
 */
export interface FileUploadResponse {
  /** 存储 key */
  key: string;
  /** 原始文件名 */
  name: string;
  /** 回显/下载代理地址（相对路径 /api/files/{key}） */
  url: string;
  /** 文件大小（字节） */
  size: number;
  /** Content-Type */
  contentType: string;
  /** 是否图片 */
  isImage: boolean;
}

/**
 * 已上传文件列表项
 */
export interface FileItem {
  key: string;
  name: string;
  size: number;
  mimeType?: string;
  /** 七牛上传时间戳（100 纳秒） */
  putTime: number;
}

/**
 * 上传状态
 */
export type UploadStatus = 'uploading' | 'success' | 'error';

/**
 * 单个上传任务状态
 */
export interface UploadTask {
  uid: string;
  file: File;
  status: UploadStatus;
  progress: number;
  error?: string;
  result?: FileUploadResponse;
}

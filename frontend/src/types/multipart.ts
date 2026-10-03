/**
 * 分片上传相关类型。
 */

/** init 请求体 */
export interface MultipartInitRequest {
  /** 原始文件名 */
  name: string;
  /** 文件大小（字节） */
  size: number;
  /** Content-Type */
  contentType: string;
  /** 客户端计算的文件指纹（断点续传找回会话） */
  fingerprint: string;
  /** 文件完整内容的 SHA-256 十六进制摘要，用于秒传 */
  contentHash: string;
}

/** 云端已确认分片 */
export interface MultipartPartItem {
  partNumber: number;
  etag: string;
  size: number;
}

/** init 响应体 */
export interface MultipartInitResponse {
  /** 秒传命中时不创建会话，因此为空 */
  sessionId?: number;
  chunkSize: number;
  /** true 表示命中未完成会话（断点续传） */
  resumed: boolean;
  /** true 表示命中相同内容的已有文件，无需上传 */
  instantUpload: boolean;
  /** 秒传命中时的已有文件信息 */
  file?: import('./file').FileUploadResponse;
  uploadedParts: MultipartPartItem[];
}

/** uploadPart 响应体 */
export interface MultipartPartResponse {
  etag: string;
}

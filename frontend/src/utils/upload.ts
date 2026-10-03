/**
 * 上传约束常量与前端预校验。
 * 数值必须与后端 application.yml 的 file.upload.* 保持一致，MIME 白名单同理；
 * 修改任一侧时需同步另一侧（本期不做配置下发）。
 */

/** 文件中心通用上传上限：50MB（对齐 file.upload.general.max-size） */
export const MAX_FILE_SIZE = 50 * 1024 * 1024;
/** 候选人照片上限：5MB（对齐 file.upload.photo.max-size） */
export const MAX_PHOTO_SIZE = 5 * 1024 * 1024;
/** 候选人简历上限：10MB（对齐 file.upload.resume.max-size） */
export const MAX_RESUME_SIZE = 10 * 1024 * 1024;

/** 文件中心允许的类型（显式列出图片 MIME，不用 image/* 通配，避免选择器放进 SVG） */
export const ACCEPT_GENERAL =
  'image/jpeg,image/png,image/gif,image/webp,image/bmp,application/pdf,.doc,.docx,.xls,.xlsx,.zip,.txt';
/** 照片允许的图片类型 */
export const ACCEPT_PHOTO = 'image/jpeg,image/png,image/gif,image/webp,image/bmp';
/** 简历允许的文档类型 */
export const ACCEPT_RESUME = 'application/pdf,.doc,.docx,.xls,.xlsx,.txt,.zip';

/** 照片 MIME 白名单（beforeUpload 精确匹配，拒绝 image/svg+xml 等伪装图片） */
export const PHOTO_MIME_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'];

/** 简历扩展名白名单 */
export const RESUME_EXTENSIONS = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.txt', '.zip'];

/** 校验文件大小：超限返回错误文案，否则返回 null */
export function checkFileSize(file: File, maxBytes: number, label: string): string | null {
  if (file.size > maxBytes) {
    return `${label}大小不能超过 ${Math.round(maxBytes / 1024 / 1024)}MB`;
  }
  return null;
}

/** 判断照片 MIME 是否在白名单内 */
export function isAllowedPhoto(file: File): boolean {
  return PHOTO_MIME_TYPES.includes(file.type);
}

/** 判断简历扩展名是否在白名单内（部分浏览器不给 Office 文档标准确 MIME，按扩展名判定） */
export function isAllowedResume(file: File): boolean {
  const name = file.name.toLowerCase();
  return RESUME_EXTENSIONS.some((ext) => name.endsWith(ext));
}

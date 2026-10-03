/**
 * 文件类型判断与图标类别。
 * 前端独立判断，避免依赖后端字段，便于扩展（视频/音频走独立回显）。
 */

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'];

export type FileCategory = 'image' | 'pdf' | 'word' | 'excel' | 'zip' | 'text' | 'unknown';

/**
 * 取扩展名（小写，不含点）。
 */
export function extension(name: string): string {
  const idx = name.lastIndexOf('.');
  if (idx < 0 || idx === name.length - 1) return '';
  return name.slice(idx + 1).toLowerCase();
}

/**
 * 是否图片类型。
 */
export function isImage(contentType: string | undefined, name: string): boolean {
  if (contentType && contentType.startsWith('image/')) return true;
  return IMAGE_EXT.includes(extension(name));
}

/**
 * 文件分类。
 */
export function category(name: string): FileCategory {
  const ext = extension(name);
  if (IMAGE_EXT.includes(ext)) return 'image';
  switch (ext) {
    case 'pdf':
      return 'pdf';
    case 'doc':
    case 'docx':
      return 'word';
    case 'xls':
    case 'xlsx':
      return 'excel';
    case 'zip':
    case 'rar':
    case '7z':
    case 'gz':
    case 'tar':
      return 'zip';
    case 'txt':
    case 'md':
    case 'log':
      return 'text';
    default:
      return 'unknown';
  }
}

/**
 * 人类可读的文件大小。
 */
export function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

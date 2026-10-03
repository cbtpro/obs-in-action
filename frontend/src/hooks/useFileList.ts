import { useCallback, useEffect, useState } from 'react';
import { listFiles, deleteFile } from '../api/files';
import { FileItem, FileUploadResponse } from '../types/file';

/**
 * 文件列表 Hook：加载、删除、上传后即时回显。
 */
export function useFileList() {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(() => {
    setLoading(true);
    const req = listFiles(100);
    req.then(list => setFiles(list))
      .catch(() => { /* 错误由 HttpClient 拦截器处理 */ })
      .finally(() => setLoading(false));
    return req;
  }, []);

  const remove = useCallback(async (key: string) => {
    await deleteFile(key);
    setFiles((prev) => prev.filter((f) => f.key !== key));
  }, []);

  /** 上传成功后本地 unshift 即时回显 */
  const unshift = useCallback((r: FileUploadResponse) => {
    setFiles((prev) => [
      { key: r.key, name: r.name, size: r.size, mimeType: r.contentType, putTime: Date.now() },
      ...prev,
    ]);
  }, []);

  useEffect(() => {
    const req = refresh();
    return () => req.cancel();
  }, [refresh]);

  return { files, loading, refresh, remove, unshift };
}

import FileUploader from '../components/FileUploader';
import ChunkUploader from '../components/ChunkUploader';
import FileListView from '../components/FileList';
import { useFileList } from '../hooks/useFileList';

/**
 * 文件管理页面：上传（单文件 + 分片）+ 列表。
 */
export default function FilePage() {
  const { files, loading, remove, unshift } = useFileList();
  return (
    <>
      <FileUploader onUploaded={unshift} />
      <ChunkUploader onUploaded={unshift} />
      <FileListView files={files} loading={loading} onDelete={remove} />
    </>
  );
}

import { useState } from 'react';
import { Button, Image, List, Popconfirm, Typography } from 'antd';
import {
  FileTextOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  FileZipOutlined,
  DownloadOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { FileItem } from '../types/file';
import { category, humanSize, isImage, type FileCategory } from '../utils/fileType';
import { getFileProxyUrl } from '../api/files';

const { Text, Link } = Typography;

interface FileListProps {
  files: FileItem[];
  loading: boolean;
  onDelete: (key: string) => void;
}

const ICON_FOR: Record<FileCategory, ReactNode> = {
  image: <FileImageOutlined />,
  pdf: <FilePdfOutlined />,
  word: <FileTextOutlined />,
  excel: <FileTextOutlined />,
  zip: <FileZipOutlined />,
  text: <FileTextOutlined />,
  unknown: <FileTextOutlined />,
};

export default function FileListView({ files, loading, onDelete }: FileListProps) {
  const [previewSrc, setPreviewSrc] = useState<string | undefined>();

  const images = files.filter((f) => isImage(f.mimeType, f.name));
  const docs = files.filter((f) => !isImage(f.mimeType, f.name));

  return (
    <div className="file-list">
      {images.length > 0 && (
        <section className="image-section">
          <h3>图片</h3>
          <div className="image-grid">
            <Image.PreviewGroup>
              {images.map((f) => (
                <div key={f.key} className="image-cell">
                  <Image
                    src={getFileProxyUrl(f.key)}
                    alt={f.name}
                    loading="lazy"
                    width="100%"
                    height={120}
                    style={{ objectFit: 'cover', borderRadius: 0 }}
                  />
                  <div className="image-meta">
                    <Text ellipsis style={{ maxWidth: '100%' }} title={f.name}>
                      {f.name}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {humanSize(f.size)}
                    </Text>
                  </div>
                </div>
              ))}
            </Image.PreviewGroup>
          </div>
        </section>
      )}

      {docs.length > 0 && (
        <section className="doc-section">
          <h3>文件</h3>
          <List
            loading={loading}
            dataSource={docs}
            renderItem={(f) => {
              const cat = category(f.name);
              const url = getFileProxyUrl(f.key);
              return (
                <List.Item
                  actions={[
                    cat === 'pdf' ? (
                      <Link key="view" href={url} target="_blank">
                        预览
                      </Link>
                    ) : null,
                    <a key="download" href={url} download={f.name}>
                      <Button type="link" icon={<DownloadOutlined />} />
                    </a>,
                    <Popconfirm
                      key="delete"
                      title="确认删除？"
                      onConfirm={() => onDelete(f.key)}
                    >
                      <Button type="link" danger icon={<DeleteOutlined />} />
                    </Popconfirm>,
                  ].filter(Boolean) as ReactNode[]}
                >
                  <List.Item.Meta
                    avatar={<span className="file-icon">{ICON_FOR[cat]}</span>}
                    title={<Text ellipsis>{f.name}</Text>}
                    description={humanSize(f.size)}
                  />
                </List.Item>
              );
            }}
          />
        </section>
      )}

      {files.length === 0 && !loading && (
        <Text type="secondary">暂无文件，点击上方区域上传</Text>
      )}

      {previewSrc && (
        <Image
          style={{ display: 'none' }}
          src={previewSrc}
          preview={{
            visible: true,
            onVisibleChange: (v) => !v && setPreviewSrc(undefined),
          }}
        />
      )}
    </div>
  );
}

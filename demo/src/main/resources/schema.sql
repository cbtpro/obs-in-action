-- 面试候选人表
CREATE TABLE IF NOT EXISTS candidate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    id_card VARCHAR(18) NOT NULL COMMENT '身份证号',
    gender VARCHAR(10) COMMENT '性别',
    age INT COMMENT '年龄',
    photo_key VARCHAR(255) COMMENT '照片存储 key',
    photo_name VARCHAR(255) COMMENT '照片原始文件名',
    resume_key VARCHAR(255) COMMENT '简历附件存储 key',
    resume_name VARCHAR(255) COMMENT '简历原始文件名',
    bio TEXT COMMENT '个人简介',
    mock_tag VARCHAR(50) COMMENT '模拟数据标志（正常数据为 NULL）',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted_at DATETIME COMMENT '软删除时间（NULL 为未删除，非 NULL 待物理清理）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_id_card (id_card)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试候选人';

-- 兼容旧表：补充列（列已存在时由 continue-on-error 忽略报错）
ALTER TABLE candidate ADD COLUMN photo_name VARCHAR(255) COMMENT '照片原始文件名' AFTER photo_key;
ALTER TABLE candidate ADD COLUMN resume_name VARCHAR(255) COMMENT '简历原始文件名' AFTER resume_key;
ALTER TABLE candidate ADD COLUMN mock_tag VARCHAR(50) COMMENT '模拟数据标志（正常数据为 NULL）';
ALTER TABLE candidate ADD COLUMN deleted_at DATETIME COMMENT '软删除时间（NULL 为未删除，非 NULL 待物理清理）';

-- 兼容旧表：补充索引（索引已存在时由 continue-on-error 忽略报错）
-- 模拟数据清理/统计接口按 mock_tag 过滤，避免全表扫描
ALTER TABLE candidate ADD INDEX idx_mock_tag (mock_tag);

-- 分片上传会话表（断点续传的服务端锚点）
CREATE TABLE IF NOT EXISTS upload_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    fingerprint VARCHAR(128) NOT NULL COMMENT '客户端文件指纹',
    content_hash CHAR(64) NOT NULL COMMENT '完整文件 SHA-256，用于秒传查重',
    storage_key VARCHAR(255) NOT NULL COMMENT '对象存储 key',
    upload_id VARCHAR(255) NOT NULL COMMENT '云端分片会话标识',
    name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    size BIGINT NOT NULL COMMENT '声明的文件大小（字节）',
    content_type VARCHAR(200) COMMENT 'Content-Type',
    image TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否图片',
    chunk_size INT NOT NULL COMMENT '单片大小（字节）',
    status VARCHAR(20) NOT NULL COMMENT '状态：UPLOADING/COMPLETED/ABORTED',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_upload_session_fp (fingerprint),
    INDEX idx_upload_session_content_hash (content_hash),
    INDEX idx_upload_session_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传会话';

-- 文件内容哈希：分片上传完成后写入，供后续相同内容秒传；软删除时清空
ALTER TABLE file_record ADD COLUMN content_hash CHAR(64) COMMENT '完整文件 SHA-256，用于秒传查重';
ALTER TABLE file_record ADD INDEX idx_file_record_content_hash_size (content_hash, size);

-- 兼容旧 upload_session 表
ALTER TABLE upload_session ADD COLUMN content_hash CHAR(64) COMMENT '完整文件 SHA-256，用于秒传查重' AFTER fingerprint;
ALTER TABLE upload_session ADD INDEX idx_upload_session_content_hash (content_hash);

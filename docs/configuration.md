# 配置参考

[返回文档首页](./README.md)

以下默认值来自 [`application.yml`](../demo/src/main/resources/application.yml)。部署时以当前代码为准。

## 基础配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端 HTTP 端口 |
| `obs.provider` | `qiniu` | 存储实现，可选 `qiniu`、`aliyun` |
| `spring.servlet.multipart.max-file-size` | `50MB` | 单文件进入应用前的请求层上限 |
| `spring.servlet.multipart.max-request-size` | `50MB` | 整个 multipart 请求上限 |

## 上传策略

| 场景 | 配置前缀 | 默认上限 | 类型范围 |
| --- | --- | --- | --- |
| 通用附件 | `file.upload.general` | 50 MB | 常见图片、PDF、ZIP、Office、文本 |
| 候选人照片 | `file.upload.photo` | 5 MB | JPEG、PNG、GIF、WebP、BMP |
| 候选人简历 | `file.upload.resume` | 10 MB | PDF、ZIP、Office、文本 |

具体 MIME 类型白名单见 [`application.yml`](../demo/src/main/resources/application.yml)。白名单是业务规则，与当前存储厂商无关。

## 七牛配置

| 配置项 | 必填 | 说明 |
| --- | --- | --- |
| `qiniu.access-key` | 是 | Access Key，建议从 `QINIU_AK` 注入 |
| `qiniu.secret-key` | 是 | Secret Key，建议从 `QINIU_SK` 注入 |
| `qiniu.bucket` | 是 | 存储空间名称 |
| `qiniu.domain` | 是 | 访问域名，建议使用 HTTPS 且末尾不带 `/` |
| `qiniu.region` | 否 | 区域，默认自动识别 |
| `qiniu.url-expire-seconds` | 否 | 私有下载 URL 有效期，默认 3600 秒 |

## 阿里云 OSS 配置

| 配置项 | 必填 | 说明 |
| --- | --- | --- |
| `aliyun.oss.access-key-id` | 是 | AccessKey ID，建议从 `ALIYUN_AK` 注入 |
| `aliyun.oss.access-key-secret` | 是 | AccessKey Secret，建议从 `ALIYUN_SK` 注入 |
| `aliyun.oss.endpoint` | 是 | OSS Endpoint |
| `aliyun.oss.bucket-name` | 是 | Bucket 名称 |

## 清理任务

| 配置前缀 | 默认状态 | 默认保留期 | 默认执行时间 |
| --- | --- | --- | --- |
| `file.cleanup` | 关闭 | 7 天 | 每天 03:00 |
| `file.multipart.cleanup` | 关闭 | 1 天 | 每天 04:00 |

⚠️ 两项任务都会物理删除数据或云端分片。上线前应在测试环境验证存储权限、保留期和查询范围，再显式设置 `enabled: true`。

## 配置安全

- 不要提交真实云厂商凭据、数据库密码或证书私钥。
- 生产环境通过密钥管理服务或受控环境变量注入敏感值。
- 数据库密码及七牛、阿里云访问密钥支持 `ENC(...)` 格式；识别原理、生成和轮换方式见[敏感配置加密](./database-password-encryption.md)。
- 私有文件使用私有存储空间和短期签名 URL。
- 修改上传上限时，同时检查请求层上限、业务策略和代理服务器限制。

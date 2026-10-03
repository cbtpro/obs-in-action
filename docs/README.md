# 项目文档

`obs-in-action` 展示了一条完整的对象存储文件链路：统一存储接口、七牛与阿里云适配、上传安全、数据一致性、分片上传和断点续传。

## 从这里开始

| 目标 | 推荐文档 |
| --- | --- |
| 在本地运行项目 | [快速开始](./getting-started.md) |
| 理解模块边界和厂商切换 | [架构与存储抽象](./architecture.md) |
| 查询所有配置项 | [配置参考](./configuration.md) |
| 加密数据库和对象存储凭据 | [敏感配置加密](./database-password-encryption.md) |
| 了解上传安全、更新和删除顺序 | [安全与数据一致性](./security-and-consistency.md) |
| 理解前端上传行为 | [前端上传](./frontend-upload.md) |
| 实现大文件与断点续传 | [分片上传与断点续传](./multipart-upload.md) |
| 定位常见故障 | [故障排查](./troubleshooting.md) |
| 阅读完整设计推导 | [完整设计说明](./file-pipeline.md) |

## 实现状态

| 能力 | 状态 | 代码入口 |
| --- | --- | --- |
| 单文件上传、下载和删除 | ✅ 已实现 | [`ObsStorage`](../obs-core/src/main/java/com/example/obs/core/ObsStorage.java) |
| 七牛与阿里云切换 | ✅ 已实现 | [`obs-qiniu`](../obs-qiniu/)、[`obs-aliyun`](../obs-aliyun/) |
| 上传类型和大小校验 | ✅ 已实现 | [`FileUploadPolicy`](../demo/src/main/java/com/example/demo/application/FileUploadPolicy.java) |
| 软删除及到期清理 | ✅ 已实现，清理任务默认关闭 | [`FileCleanupScheduler`](../demo/src/main/java/com/example/demo/application/FileCleanupScheduler.java) |
| 分片上传和断点续传 | ✅ 已实现 | [`ObsMultipartStorage`](../obs-core/src/main/java/com/example/obs/core/ObsMultipartStorage.java) |
| 分片 Content-MD5 校验 | ✅ 已实现，客户端、服务端和厂商三段校验 | [分片完整性](./multipart-upload.md#分片-content-md5) |
| 基于 SHA-256 的内容秒传 | ✅ 已实现，完成后服务端复核 | [内容秒传](./multipart-upload.md#内容秒传) |
| 孤儿分片清理 | ✅ 已实现，清理任务默认关闭 | [`MultipartCleanupScheduler`](../demo/src/main/java/com/example/demo/application/MultipartCleanupScheduler.java) |
| 下载加密 | 📌 预留接口，尚未实现 | [`FileDownloadService`](../demo/src/main/java/com/example/demo/application/FileDownloadService.java) |

## 模块地图

```text
frontend ──HTTP──► demo ──接口──► obs-core
                              ▲
                              ├── obs-qiniu
                              └── obs-aliyun
```

- [`frontend`](../frontend/)：React 上传界面和分片上传客户端。
- [`demo`](../demo/)：业务用例、REST 接口、持久化和清理任务。
- [`obs-core`](../obs-core/)：与厂商无关的存储接口和数据类型。
- [`obs-qiniu`](../obs-qiniu/)：七牛云适配。
- [`obs-aliyun`](../obs-aliyun/)：阿里云 OSS 适配。

## 文档约定

- ✅ 表示仓库中已有可运行实现；📌 表示设计预留或后续方向；⚠️ 表示部署前必须确认的风险。
- 文档解释设计意图，实际接口、默认值和依赖版本以代码为准。
- 新增能力时同时更新本页状态表和相关专题文档。
- 示例只使用占位凭据；生产密钥、证书私钥和本地配置不得提交到仓库。

# 分片上传与断点续传

[返回文档首页](./README.md)

## 何时使用

分片上传主要解决内存、失败重传成本和弱网吞吐问题。是否启用应由文件大小、网络质量、并发量和服务端内存共同决定。

“100 MB 以下不分片”只是本项目的经验判断，不是协议限制。应通过真实流量测试确定阈值。

## 生命周期

```text
init ──► uploadPart × N ──► listParts 校验 ──► complete
  │                                                │
  └────────────── cancel / expired ──► abort ◄────┘
```

✅ [`ObsMultipartStorage`](../obs-core/src/main/java/com/example/obs/core/ObsMultipartStorage.java) 定义了这组独立能力。七牛实现使用 4 MB 分片，阿里云实现使用 5 MB 分片，服务端在 init 响应中把实际值交给前端。

## REST 接口

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/multipart/init` | 新建或恢复上传会话 |
| `PUT` | `/api/multipart/{sessionId}/parts/{partNumber}` | 上传原始分片字节 |
| `POST` | `/api/multipart/{sessionId}/complete` | 校验并合并分片 |
| `DELETE` | `/api/multipart/{sessionId}` | 取消会话并清理云端分片 |

路径暴露业务会话 ID，不暴露厂商的 uploadId。实现见 [`MultipartController`](../demo/src/main/java/com/example/demo/interfaces/rest/MultipartController.java)。

## 状态归属

数据库保存会话元数据，云端 `listParts` 是分片状态的唯一权威来源：

```text
数据库：文件指纹、uploadId、对象键、声明大小、分片大小、会话状态
云端：  实际收到的分片号、大小和 etag
```

分片成功后不重复写本地分片表，从而避免“云端成功但本地失败”或反向的不一致。

## 断点续传

前端用文件名、大小和最后修改时间生成指纹。init 时服务端按指纹寻找上传中的会话，再调用云端 `listParts` 对账：

```text
选择文件 ──► 生成指纹 ──► init ──► 云端对账 ──► 跳过已确认分片 ──► complete
```

该指纹不是内容哈希，只适合找回上传会话。发生碰撞时，complete 阶段的总大小校验会拒绝明显不一致的数据，但它不应被当成安全或去重凭据。

## 分片 Content-MD5

✅ 每个分片上传前，前端使用 `spark-md5` 计算 MD5，并按 HTTP `Content-MD5` 约定转换为 Base64：

```text
分片 Blob ──MD5 + Base64──► Content-MD5 请求头
                                  │
                                  ▼
                         服务端重新计算并比较
                                  │ 一致
                                  ▼
                     七牛 Content-MD5 / 阿里云 md5Digest
```

服务端不直接信任请求头。摘要缺失、Base64 格式错误或与请求体不一致时返回业务错误 `40009`，请求不会到达对象存储。实现入口：

- 前端：[`ChunkUploader.tsx`](../frontend/src/components/ChunkUploader.tsx)
- HTTP 接口：[`MultipartController`](../demo/src/main/java/com/example/demo/interfaces/rest/MultipartController.java)
- 业务校验：[`MultipartUploadApplicationService`](../demo/src/main/java/com/example/demo/application/MultipartUploadApplicationService.java)
- 厂商透传：[`QiniuMultipartStorage`](../obs-qiniu/src/main/java/com/example/obs/qiniu/QiniuMultipartStorage.java)、[`AliyunMultipartStorage`](../obs-aliyun/src/main/java/com/example/obs/aliyun/AliyunMultipartStorage.java)

MD5 在这里用于传输错误检测，不用于安全签名或内容身份。文件身份使用 SHA-256。

## 内容秒传

✅ init 请求携带完整文件的 SHA-256 十六进制摘要。服务端按 `content_hash + size` 查询未删除的文件：

```text
前端计算完整 SHA-256 ──► init
                            ├── 命中 ──► 直接返回已有文件，不创建上传会话
                            └── 未命中 ──► 正常分片上传
                                              │
                                              ▼
                                    complete 后下载云端对象流
                                              │
                                    服务端复算并比较 SHA-256
                                      ├── 一致：保存哈希索引
                                      ├── 不一致：删除对象并拒绝完成
                                      └── 无法读取：上传成功，但不保存哈希索引
```

服务端完成后的二次校验很重要：客户端上报的哈希只能用于提前查询，不能直接成为可信索引。确认内容不一致时返回 `40011`。如果下载域名、TLS 或网络暂时不可用，文件上传仍然成功，但该文件的 `content_hash` 留空，不参与后续秒传；修复下载配置后的新上传会恢复秒传索引。

内容哈希分别保存在 `upload_session.content_hash` 和 `file_record.content_hash`。文件软删除时会清空哈希，使已删除文件不再参与秒传命中。

当前秒传复用已有文件记录和对象，不新增一条同名业务记录，因此响应中的文件名可能是首次上传时保存的名称。

## 完整性与幂等

上传单片前校验：

- 分片号位于 1～10000；
- 会话存在且状态为 `UPLOADING`；
- 分片非空且不超过会话记录的分片大小。

complete 时从云端重新获取分片列表，要求列表非空且总大小等于 init 声明大小。已完成会话再次 complete 时直接返回已有结果。

## 清理孤儿分片

✅ [`MultipartCleanupScheduler`](../demo/src/main/java/com/example/demo/application/MultipartCleanupScheduler.java) 会清理超期的上传中会话，但默认关闭。云端生命周期规则仍应作为第二道兜底。

厂商对 uploadId、分片大小和生命周期的约束可能变化。上线前应根据当前 SDK 版本和厂商官方文档复核，而不是只依赖本文经验值。

## 尚未实现的扩展

- 📌 跨文件任务的全局并发限制。
- 📌 多租户或权限域内去重；当前示例没有租户维度。
- 📌 使用 Web Worker 或流式 SHA-256 降低超大文件哈希对主线程和内存的影响。

这些能力应在流量、错误率或成本数据证明有必要后引入。

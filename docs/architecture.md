# 架构与存储抽象

[返回文档首页](./README.md)

## 目标

业务代码不感知具体厂商。切换七牛与阿里云时，只修改：

```yaml
obs:
  provider: aliyun # 或 qiniu
```

## 模块职责

```text
frontend (React)
    │ HTTP
    ▼
demo (业务编排、REST、持久化)
    │ 只依赖接口
    ▼
obs-core (纯 Java 存储契约)
    ▲
    ├── obs-qiniu  (七牛 SDK + 自动装配)
    └── obs-aliyun (阿里云 SDK + 自动装配)
```

| 模块 | 职责 | 不应包含 |
| --- | --- | --- |
| `obs-core` | 稳定接口和数据类型 | Spring Web 类型、厂商 SDK |
| `obs-qiniu` | 七牛协议适配和自动装配 | 业务校验 |
| `obs-aliyun` | 阿里云协议适配和自动装配 | 业务校验 |
| `demo` | 上传规则、事务、补偿和 REST | 厂商专属调用 |
| `frontend` | 交互、预校验、进度和取消 | 存储凭据 |

## 稳定接口

✅ 单文件能力由 [`ObsStorage`](../obs-core/src/main/java/com/example/obs/core/ObsStorage.java) 定义：

```java
public interface ObsStorage {
    ObsUploadResult upload(ObsUploadRequest request);
    ObsDownloadResult download(String key);
    void delete(String key);
    String getUrl(String key);
}
```

接口约定比方法数量更重要：

- MIME 类型必须由业务校验后显式传入，适配层不能按扩展名猜测。
- 删除不存在的对象视为成功，保证重试幂等。
- 下载失败时必须关闭响应流，避免耗尽连接池。
- `obs-core` 使用 `byte[]`、`InputStream` 等 Java 类型，不依赖 `MultipartFile`。

✅ 分片能力单独放在 [`ObsMultipartStorage`](../obs-core/src/main/java/com/example/obs/core/ObsMultipartStorage.java)，避免不支持分片的实现被迫提供空方法。

## 自动装配

两种实现分别通过条件装配注册 Bean：

- [`QiniuAutoConfiguration`](../obs-qiniu/src/main/java/com/example/obs/qiniu/QiniuAutoConfiguration.java)
- [`AliyunAutoConfiguration`](../obs-aliyun/src/main/java/com/example/obs/aliyun/AliyunAutoConfiguration.java)

`@ConditionalOnProperty` 根据 `obs.provider` 选择实现，`@ConditionalOnMissingBean` 允许调用方覆盖默认 Bean。

## 验收标准

1. `demo` 中没有导入厂商 SDK 类型。
2. 两种 provider 分别运行存储适配测试。
3. 只切换 `obs.provider`，业务代码和前端无需修改。
4. 两种实现对删除、异常和 MIME 类型遵循相同语义。

更多设计推导见[完整设计说明](./file-pipeline.md)。

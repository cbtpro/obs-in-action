# 安全与数据一致性

[返回文档首页](./README.md)

## 上传安全边界

✅ [`FileUploadPolicy`](../demo/src/main/java/com/example/demo/application/FileUploadPolicy.java) 按通用附件、照片和简历三种场景执行类型与大小校验。

上传需要三层控制：

1. 前端预校验，尽早反馈并节省带宽。
2. Spring multipart 请求上限，避免超大请求进入应用层。
3. 应用层精确白名单，执行最终业务判断。

不要使用 `image/*` 作为可信白名单。SVG 可以包含主动内容，不应像 JPEG、PNG 一样默认内联预览。下载响应还应设置 `X-Content-Type-Options: nosniff`，阻止浏览器重新猜测类型。

## 对象键与原始文件名

对象键（key）由服务端生成，原始文件名只作为业务元数据保存：

```text
客户端原始文件名 ──► 清理和留存展示
服务端 UUID       ──► 云端对象键
```

这样可以同时避免路径穿越、同名覆盖和 URL 中的信息泄露。下载时通过 `Content-Disposition` 恢复用户可见文件名。

## 上传与数据库写入

对象存储和数据库之间没有本地事务，采用补偿策略：

```text
上传对象 ──成功──► 写数据库
   ▲                 │
   └── 删除新对象 ◄──┘ 写库失败
```

设计原则是优先避免数据库记录指向不存在的对象。偶发孤儿对象可以由清理任务处理，而用户可见的丢失更难修复。

## 替换文件

正确顺序是：

```text
上传新对象 ──► 数据库指向新对象 ──► 事务提交 ──► 删除旧对象
                  │ 写库失败
                  └──► 删除新对象；旧对象保持不变
```

旧对象必须在事务提交后删除。提交后回调中的删除异常不能继续向上抛，否则会把已经成功的业务操作表现成 HTTP 500；应记录日志并交给清理机制兜底。

## 两级删除

```text
删除请求 ──► 设置 deleted_at ──保留期──► 删除云端对象 ──► 删除数据库行
                  │
                  └──保留期内可恢复
```

✅ [`FileCleanupScheduler`](../demo/src/main/java/com/example/demo/application/FileCleanupScheduler.java) 负责到期清理，但默认关闭。启用方法见[配置参考](./configuration.md#清理任务)。

⚠️ 软删除记录仍可能占用唯一索引。每个查询都要明确是否应该看到已删除记录，不能机械地为所有查询追加相同过滤条件。

## 下载安全

- 私有文件使用有期限的签名 URL。
- 只有明确安全的图片类型和 PDF 可以内联展示；其余类型强制下载。
- 设置 `nosniff`，并对 UTF-8 文件名正确编码。
- 所有成功或异常路径都要明确响应流的所有权和关闭责任。

📌 [`FileDownloadService`](../demo/src/main/java/com/example/demo/application/FileDownloadService.java) 已为下载加密预留完整响应语义，但加密实现尚未落地。

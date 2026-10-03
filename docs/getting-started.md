# 快速开始

[返回文档首页](./README.md)

## 环境要求

- JDK 21
- Maven 3.9 或兼容版本
- Node.js 18 或更高版本
- pnpm
- MySQL 8.x
- 一个七牛云或阿里云 OSS 存储空间

项目当前依赖版本可在根目录 [`pom.xml`](../pom.xml) 和 [`frontend/package.json`](../frontend/package.json) 中确认。

## 1. 准备数据库

默认连接配置为：

```text
地址：localhost:3306
数据库：obs-in-action
用户名：obs-in-action
密码：无明文默认值；通过 `MYSQL_PASSWORD` 或 `ENC(...)` 配置
```

可通过 `MYSQL_URL`、`MYSQL_USER` 和 `MYSQL_PASSWORD` 环境变量覆盖。配置文件中的密码也支持 `ENC(...)` 密文，使用方法见[敏感配置加密](./database-password-encryption.md)。表结构位于 [`schema.sql`](../demo/src/main/resources/schema.sql)，应用启动时会尝试初始化。

⚠️ 默认账号仅适合本地演示。共享环境和生产环境必须使用独立强密码，并关闭不必要的自动建表行为。

## 2. 配置对象存储

复制 [`application-local.yml.example`](../demo/src/main/resources/application-local.yml.example) 为本地的 `application-local.yml`，选择一种存储实现并填写对应配置。

七牛：

```yaml
obs:
  provider: qiniu

qiniu:
  access-key: ${QINIU_AK}
  secret-key: ${QINIU_SK}
  bucket: ${QINIU_BUCKET}
  domain: ${QINIU_DOMAIN}
```

阿里云：

```yaml
obs:
  provider: aliyun

aliyun:
  oss:
    access-key-id: ${ALIYUN_AK}
    access-key-secret: ${ALIYUN_SK}
    endpoint: ${ALIYUN_ENDPOINT}
    bucket-name: ${ALIYUN_BUCKET}
```

优先使用环境变量，不要把真实密钥写入版本库。完整配置见[配置参考](./configuration.md)。

## 3. 启动后端

在项目根目录执行：

```bash
mvn clean test
mvn -pl demo -am spring-boot:run
```

后端默认监听 `http://localhost:8080`。

## 4. 准备本地 HTTPS 证书

前端开发服务器会读取 `frontend/.ssl/key.pem` 和 `frontend/.ssl/cert.pem`。在项目根目录运行：

```bash
./frontend/scripts/generate-dev-cert.sh
```

脚本行为：

- 优先使用 `mkcert`，安装本地开发 CA 并生成浏览器可信任的证书；系统可能要求确认修改本机信任设置。
- 未安装 `mkcert` 时，使用 OpenSSL 生成有效期 365 天的自签名证书。
- 证书覆盖 `localhost`、`127.0.0.1` 和 `::1`。
- 私钥权限设置为仅当前用户可读写，证书设置为所有用户可读。

如果使用 OpenSSL 的降级方案，浏览器首次访问可能显示安全警告。需要无警告的本地 HTTPS 时，安装 `mkcert` 后重新运行脚本。

脚本重复运行会替换现有的本地开发证书。不要将该脚本用于生成生产证书。

⚠️ `frontend/.ssl/` 整个目录已被 `.gitignore` 排除。不要提交生产证书、开发证书私钥或本地 CA 私钥；仓库只保留生成脚本和说明。

## 5. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认监听 `https://localhost:5173`，并将 `/api` 代理到后端的 8080 端口。

## 6. 最小验证

1. 打开前端页面。
2. 上传一张允许类型且小于 5 MB 的候选人照片。
3. 确认详情页可以预览。
4. 下载文件并确认原始文件名正确。
5. 将 `obs.provider` 切换到另一厂商，重启后端并重复验证。

如果启动或上传失败，参见[故障排查](./troubleshooting.md)。

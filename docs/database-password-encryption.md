# 敏感配置加密：数据库与对象存储

[返回文档首页](./README.md)

## 目标与边界

`demo` 和 `task-service` 支持用 Jasypt 的 `ENC(...)` 格式保存敏感配置。适用范围包括数据库密码、七牛 Access Key/Secret Key 和阿里云 OSS AccessKey ID/Secret。配置文件只保存密文，解密主密钥通过运行环境提供。

需要区分两件事：

- 本文保护的是应用配置中的数据库连接密码，不会修改 MySQL 用户表里的密码。
- 对象存储密钥加密只保护应用配置，不会轮换或修改云厂商控制台中的密钥。
- `ENC(...)` 避免配置文件直接暴露明文，但不能替代密钥管理、文件权限和数据库最小权限。

## 框架如何识别加密值

`ENC(...)` 不是 Spring Boot 原生语法。宿主应用引入 [`jasypt-spring-boot-starter`](../demo/pom.xml) 后，Jasypt 会包装 Spring 的配置环境，并在属性绑定前检查每个配置值：

```text
application.yml / 环境配置
          │
          ▼
Spring Boot 加载 PropertySource
          │
          ▼
Jasypt 检查属性值
    ├── 普通字符串 ──► 原样返回
    └── ENC(...)   ──► 使用 JASYPT_ENCRYPTOR_PASSWORD 解密
                              │
                              ▼
                绑定到 DataSource、QiniuProperties
                       或 AliyunProperties
```

因此业务类和配置属性类不需要调用解密 API。例如，`QiniuProperties#getSecretKey()` 和阿里云配置对象最终取得的都是解密后的值。

识别规则如下：

- 值以 `ENC(` 开头并以 `)` 结尾时，Jasypt 将其视为密文。
- 普通字符串或环境变量提供的明文不会触发解密。
- `JASYPT_ENCRYPTOR_PASSWORD` 缺失、错误或算法不匹配时，`ENC(...)` 无法解密，应用应启动失败。

解密能力属于宿主应用。当前 [`demo`](../demo/pom.xml) 和 [`task-service`](../task-service/pom.xml) 已引入 starter；`obs-core`、`obs-qiniu` 和 `obs-aliyun` 本身不负责解密。如果这些 OBS 模块被其他应用单独引用，新的宿主应用也必须提供 Jasypt 或等价的配置解密机制。

## 当前配置

两个服务的数据库密码均采用以下形式：

```yaml
spring:
  datasource:
    password: ${MYSQL_PASSWORD:ENC(REPLACE_WITH_ENCRYPTED_PASSWORD)}
```

这提供两种启动方式：

1. 推荐在受控部署环境中直接设置 `MYSQL_PASSWORD`，不需要在文件中保存密文。
2. 需要提交可部署配置时，将占位内容替换为脚本生成的 `ENC(...)`，并在运行时提供主密钥。

占位值不是有效密文。未设置 `MYSQL_PASSWORD` 且未替换占位值时，应用会启动失败，这是为了避免静默使用弱默认密码。

## 七牛和阿里云密钥加密

七牛和阿里云配置同样经过 Spring Environment，因此可以直接使用 `ENC(...)`：

```yaml
qiniu:
  access-key: ENC(七牛AccessKey密文)
  secret-key: ENC(七牛SecretKey密文)
  bucket: your-bucket
  domain: https://cdn.example.com

aliyun:
  oss:
    access-key-id: ENC(阿里云AccessKeyId密文)
    access-key-secret: ENC(阿里云AccessKeySecret密文)
    endpoint: oss-cn-hangzhou.aliyuncs.com
    bucket-name: your-bucket
```

也可以保留环境变量优先、密文兜底的形式：

```yaml
qiniu:
  access-key: ${QINIU_AK:ENC(七牛AccessKey密文)}
  secret-key: ${QINIU_SK:ENC(七牛SecretKey密文)}

aliyun:
  oss:
    access-key-id: ${ALIYUN_AK:ENC(阿里云AccessKeyId密文)}
    access-key-secret: ${ALIYUN_SK:ENC(阿里云AccessKeySecret密文)}
```

需要分别对每一个原始值运行加密脚本。Access Key ID 有时被当成标识符而非秘密，但它仍属于凭据的一部分；在可提交配置中建议与 Secret 一起保护。

生产环境优先使用云平台 Secret 或 KMS 注入 `QINIU_AK`、`QINIU_SK`、`ALIYUN_AK` 和 `ALIYUN_SK`。只有确实需要在配置文件中保存可部署值时，才使用 `ENC(...)` 密文兜底。

## 生成密文

在项目根目录运行：

```bash
./scripts/encrypt-config-value.sh
```

脚本可以加密数据库密码或任意对象存储密钥。它会隐藏读取待加密值和两次主密钥，然后输出：

```text
ENC(...)
```

也可以把待加密值作为第一个参数传入：

```bash
./scripts/encrypt-config-value.sh 'value-to-encrypt'
```

⚠️ 命令行参数可能出现在 shell 历史或进程列表中。对真实密码应使用无参数方式。

首次运行时 Maven 会下载 `jasypt-maven-plugin`。脚本不会把明文、主密钥或密文写入文件。

## 替换配置

将脚本输出完整替换到对应环境的配置中：

```yaml
spring:
  datasource:
    password: ENC(脚本输出的密文内容)
```

如果保留环境变量覆盖形式：

```yaml
spring:
  datasource:
    password: ${MYSQL_PASSWORD:ENC(脚本输出的密文内容)}
```

不要对已经包含 `ENC(...)` 的完整字符串再次加密；脚本输入应该是数据库密码、Secret Key 等原始值。

## 启动应用

启动前设置与加密时相同的主密钥：

```bash
export JASYPT_ENCRYPTOR_PASSWORD='your-master-password'
mvn -pl demo -am spring-boot:run
```

`task-service` 使用同样的环境变量：

```bash
export JASYPT_ENCRYPTOR_PASSWORD='your-master-password'
mvn -pl task-service -am spring-boot:run
```

如果通过容器或编排平台部署，应使用 Secret 注入环境变量，不要把主密钥写入 Dockerfile、镜像、启动脚本或版本库。

## 轮换密码或访问密钥

1. 在数据库或云厂商控制台创建新的密码或访问密钥。
2. 使用现有或新主密钥重新生成 `ENC(...)`。
3. 同步更新配置密文和运行环境中的主密钥。
4. 重启服务并验证数据库连接。
5. 确认所有实例切换完成后，撤销旧数据库密码、云访问密钥和旧主密钥。

密文使用随机盐和初始化向量；同一明文和主密钥多次加密得到不同密文是正常现象。

## 常见错误

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动时报无法解密 | 主密钥缺失或与加密时不一致 | 检查 `JASYPT_ENCRYPTOR_PASSWORD` |
| 数据库认证失败 | 密文解密成功，但明文本身不正确 | 重新核对数据库账号并生成密文 |
| OBS 返回签名或认证错误 | Access Key 或 Secret 解密后不匹配 | 分别核对两项凭据并重新加密 |
| 把 `ENC(...)` 原样当作密码 | 服务未加载 Jasypt starter | 检查服务 POM 中的依赖 |
| 本地能启动、容器不能启动 | 容器没有注入主密钥 | 检查容器 Secret 和环境变量 |

## 安全要求

- 主密钥应与数据库密码分开保存和授权。
- 不在日志、聊天记录、CI 参数或问题单中粘贴明文和主密钥。
- CI/CD 使用平台 Secret，并限制可读取人员和任务。
- 为 `demo` 和 `task-service` 使用最小权限数据库账号；条件允许时使用不同账号。
- 为对象存储访问密钥配置最小权限，不使用云账号的高权限长期密钥。
- 定期轮换数据库密码、对象存储密钥和主密钥，并保留可回滚的短暂重叠窗口。

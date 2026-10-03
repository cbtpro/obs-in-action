# 把文件上传做对：一条对象存储链路的设计与取舍

> 本文对应仓库中的完整示例实现。若只想查找代码入口，可直接查看文末的[代码导航](#代码导航)。

> 本文保留完整的设计推导，适合连续阅读。按任务查阅时，建议从[文档首页](./README.md)进入拆分后的专题文档。

## 阅读说明

- ✅ 已实现：仓库中已有代码和测试。
- 📌 扩展方向：接口或设计已考虑，但仓库中没有完整实现。
- ⚠️ 风险提示：部署或修改前需要显式确认。

术语统一如下：对象存储空间称为 bucket；对象在 bucket 中的唯一标识称为对象键（key）；云厂商创建的分片上传句柄称为 uploadId；HTTP 内容类型称为 MIME 类型。术语第一次出现后，正文可能保留代码中的英文名称。

## 目录

- [一、架构总览：一行配置切换存储厂商](#一架构总览一行配置切换存储厂商)
- [二、存储接口：先定义稳定约定](#二存储接口先定义稳定约定)
- [三、厂商适配与自动装配](#三厂商适配与自动装配)
- [四、用第二个厂商验收抽象](#四用第二个厂商验收抽象)
- [五、业务层：校验、对象键、补偿与删除](#五业务层校验对象键补偿与删除)
- [六、前端：上传体验与浏览器边界](#六前端上传体验与浏览器边界)
- [七、分片上传：什么时候需要](#七分片上传什么时候需要)
- [八、断点续传：云端状态优先](#八断点续传云端状态优先)
- [排坑速查](#排坑速查)
- [代码导航](#代码导航)

文件上传是最容易被低估的模块。表单提交、落盘、回显 URL，任何框架半小时能跑通；但把对象存储、私有 bucket、安全边界、一致性、断点续传这些约束叠上去，每个环节都藏着必须想清楚的取舍：

传上来的文件可能是伪装的脚本；签名 URL 会过期；删数据时先删数据库还是先删对象；用户传到一半断网了怎么办。

先交代背景，不然后文的很多决策会悬在半空：文中所有代码和结构来自一个完整实现的示例项目——简历管理。候选人档案带照片和简历两类附件，覆盖上传、回显、下载、删除的完整生命周期；后端 Spring Boot，前端 React，存储层接七牛和阿里云两家，一行配置切换。你可以把这个场景代入任何"有文件附件"的业务：校验规则、保留期换成你的就行，骨架不变。

这篇文章整理其中的设计决策：从单文件直传到分片、断点续传，都是跑通了的实现，附上接口定义、表结构和关键代码。每个话题我会先把背景讲清楚再谈做法，只要你写过 Spring Boot 和 React，应该都能跟上。

## 一、架构总览：一行配置切换存储厂商

### 为什么多厂商抽象不是过度设计

很多团队的第一个存储选型是拍脑袋定的，之后就在那条路上走到黑。但存储厂商会变，理由五花八门：价格谈判桌上需要一张底牌；产品要出海，海外节点和国内厂商的覆盖是两回事；合规要求数据落在指定区域；甚至单纯是某次大规模故障之后，CTO 在复盘会上拍桌子。

等到那天再动手，业务代码里早就撒满了 `qiniuClient.upload(...)`。全局替换改一轮，测试补一轮，上线提心吊胆——这是把"可替换"当成运气而不是设计。

所以动手之前先定验收标准：**业务代码换存储厂商，唯一允许的改动是一行配置**。

不是 `全局搜索替换`，不是"新写一个 service 然后把调用点都改一遍"。就一行。

`obs.provider: qiniu` 改成 `obs.provider: aliyun`，完事。

### 四个模块怎么分工

为了这一行，整个项目拆成了四个 Maven 模块：

```text
frontend (React)
   │  multipart/form-data
   ▼
demo (业务层, DDD)
   │  FileApplicationService ──只依赖──► obs-core 接口
   ▼
obs-core (纯接口, 零依赖)
   ▲
   ├── obs-qiniu  (七牛实现 + AutoConfiguration)
   └── obs-aliyun (阿里云实现 + AutoConfiguration)
```

分工值得逐个说：

**obs-core 是整个设计的支点**。它是一个 Spring 依赖都没有的纯 Java 模块，里面只有 [`ObsStorage`](../obs-core/src/main/java/com/example/obs/core/ObsStorage.java) 接口和几个数据载体（上传请求、上传结果、下载结果、异常）。为什么克制到这个程度？因为依赖是单向流动的——业务层编译期只需要看到接口签名，具体实现在运行期由 Spring 注入。接口模块每多引一个依赖，就多一分"将来换厂商时被无关 API 绑架"的风险。一个只有接口的 jar，永远不会过时。

**obs-qiniu 和 obs-aliyun 只依赖 obs-core**，各自封装厂商 SDK，对外暴露的唯一的门是 AutoConfiguration——下面第三章展开。两个实现模块互相不知道对方存在，永远不需要知道。

**demo 是业务模块**（示例项目里就叫这个名字，候选人管理、文件管理都在里面），按 DDD 分层组织（application / domain / infrastructure / interfaces 四层，后面第五章细讲）。它对存储的全部认知就是 `ObsStorage` 这个接口——demo 的代码从不 import obs-qiniu / obs-aliyun 的任何类，这两个模块只作为运行期依赖躺在 pom 里，职责是把自己的 AutoConfiguration 送进 classpath。编译期能看到什么，耦合就有多深。

评审的时候有同事问：至于吗，就四个方法？后来接阿里云的时候，demo 模块一行代码没动，他就没再问了。

## 二、存储接口：先定义稳定约定

接口本身的四个方法，看一眼就懂：upload、download、delete、getUrl。

```java
public interface ObsStorage {
    ObsUploadResult upload(ObsUploadRequest request);
    ObsDownloadResult download(String key);
    /** 删除。对象不存在视为成功（幂等），所有实现必须遵守 */
    void delete(String key);
    /** 公有 bucket 永久直链；私有 bucket 签名临时 URL */
    String getUrl(String key);
}
```

`ObsUploadRequest` 里放的是 `byte[]` 而不是 `MultipartFile`——刻意的。一旦接口沾了 Spring Web 的类型，"纯 Java 的 obs-core"就破功了，命令行工具、定时任务这类没有 Web 上下文的调用方也没法用。数据、key、Content-Type、原始文件名、大小，五个字段，谁来调用都凑得齐。

真正值钱的不是方法签名，是接口上定死的几条约定。这些约定才是业务层不用写 if-else 的原因——所有的脏活脏判断，都在实现层内部消化掉了。

### 约定一：mimeType 必须显式传，实现层不许自作聪明

先补一点背景。HTTP 响应里有个 `Content-Type` 头，告诉浏览器"这个响应体是什么"。浏览器拿到 `image/png` 就渲染图片，拿到 `text/html` 就当页面渲染——而 HTML 和 SVG 里是可以写 `<script>` 的。这就是全部的攻击原材料：只要能让"用户上传的文件"以 `text/html` 或 `image/svg+xml` 的身份在**你的域名下**被浏览器渲染，文件里的脚本就会执行，能读你域名下的 Cookie、带着用户身份发请求。这条链路有个名字：存储型 XSS。

而七牛的上传接口恰好留了一扇门。它有个三参重载，不传 mimeType 时按 key 的扩展名探测 Content-Type——用户把恶意脚本存成 `evil.svg`，七牛就老老实实给它标上 `image/svg+xml`。如果下载接口再对 `image/*` 一律 inline 预览（浏览器就地渲染而不是下载），闭环就完成了。

所以探测这件事只能发生在业务层校验之后，实现层只忠实转存。七牛那边换成六参 `put`，显式带上服务端校验过的 contentType：

```java
// 推荐：显式 mimeType，杜绝服务端按扩展名探测
Response resp = uploadManager.put(data, key, token, null, mimeType, false);
// 反例：三参 put，按 key 扩展名猜 Content-Type，伪装文件有了可乘之机
```

### 约定二：delete 必须幂等

幂等这个词值得用一句话讲透：**同一个操作执行一次和执行多次，结果一致**。删除"一个不存在的对象"是幂等的最佳教材——第一次删除成功了，重试时对象已经没了，这次重试应该算成功，而不是抛一个"对象不存在"的异常。

为什么这条值得写进接口约定？因为上层迟早要写补偿和重试逻辑，而重试就意味着"同样的删除会发生第二次"。delete 不幂等，每个调用点都得自己长出特判，写着写着总有一个人漏写。约定写死之后，上层的补偿代码可以放心大胆地"删错都无所谓"：

```java
try {
    bucketManager.delete(bucket, key);
} catch (QiniuException e) {
    if (e.code != 612) {   // 612 = 指定的删除目标不存在，视为删除成功
        throw new ObsException("删除失败", e);
    }
}
```

各家厂商对"删不存在"的行为并不统一：七牛返回 612，阿里云干脆静默成功。实现层的职责就是把这些方言翻译成同一句话：删不存在 = 成功。

### 约定三：download 失败必须关流

HTTP 客户端的连接是池化资源：请求结束、响应体读完关闭，连接才还回池子给下一个请求用。拿到响应流之后如果不读完也不关闭，这条连接就永远回不了池——池子被这样一条条抽干，直到某天某个请求排队超时。

这个坑阴险在它几乎不可能被功能测试发现：测试用例一跑就几个请求，连接池远没见底。等到压测或者线上流量起来，问题是"系统跑着跑着变慢然后超时"，日志里没有任何直接线索。所以非 200 的响应，先 `close()` 再抛异常，一个字符都不能省。

## 三、厂商适配与自动装配

Provider 模块的核心问题只有一个：怎么让使用方"引依赖、写配置"，然后一切自动就位？

只写过业务系统的读者，习惯的是 `@Service` + 包扫描。但 SDK 封装是类库，类库没有立场要求使用方扫描你的包——你连人家主包名都不知道。Spring Boot 给类库的正规答案是 AutoConfiguration：**类库自己声明"满足什么条件时注册哪些 Bean"，Spring Boot 启动时自动装配**。

第一步，注册。在模块的 `META-INF/spring/` 下放一个 imports 文件，内容只有一行：

```text
com.example.obs.qiniu.QiniuAutoConfiguration
```

第二步，写装配类。整个类不到四十行，但每一行都有讲究：

```java
@AutoConfiguration
@EnableConfigurationProperties(QiniuProperties.class)
@ConditionalOnProperty(name = "obs.provider", havingValue = "qiniu", matchIfMissing = true)
public class QiniuAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Auth auth(QiniuProperties props) {
        return Auth.create(props.getAccessKey(), props.getSecretKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public ObsStorage obsStorage(QiniuProperties props, Auth auth,
                                 UploadManager uploadManager, BucketManager bucketManager) {
        return new QiniuObsStorage(props, auth, uploadManager, bucketManager);
    }
}
```

逐个注解读一遍：

`@ConditionalOnProperty(name = "obs.provider", havingValue = "qiniu")`——配置里 `obs.provider` 等于 qiniu 时，这套 Bean 才生效。这就是"一行配置切厂商"的机关：切到 aliyun 时七牛的整套装配自动失活，阿里云的自动上位，业务代码无感。

`matchIfMissing = true`——用户一行配置都没写时，七牛兜底。第一个接入的厂商当默认值，对 demo 项目是最合理的默认行为。生产系统我倾向反过来（不写就报错，逼你显式声明），这类"沉默的默认值"在生产环境是排查半天配置去哪了的经典来源。

`@ConditionalOnMissingBean`——每个 Bean 都挂上它，意味着使用方随时可以注册自己的同名 Bean 覆盖任何一环。比如测试环境想塞一个走代理的 `Auth`，或者给 `ObsStorage` 套一层埋点装饰器，直接自己声明 Bean 就行，不用动类库代码。这是类库给使用方保留的逃生门。

配置属性类则平平无奇，`accessKey`、`secretKey`、`bucket`、`domain`，外加一个签名 URL 有效期。完整默认值和环境变量入口见[配置参考](./configuration.md)。签名 URL 值得多说两句。

### 签名 URL：私有 bucket 的门禁

私有 bucket 的对象，直接拼 URL 访问是 403。访问要在 URL 上附签名——七牛的规则是 `domain/key?e=<过期时间戳>` 加上用 SK 算出的签名。服务端生成这个带签名的完整 URL 下发给前端，前端在有效期内就能直接访问对象存储，流量不过你的服务器。

```java
public String getUrl(String key) {
    String publicUrl = domain + "/" + key;
    if (!privateBucket) return publicUrl;              // 公有 bucket：永久直链
    return auth.privateDownloadUrl(publicUrl, props.getUrlExpireSeconds());
}
```

有效期硬编码 1 小时的话，迟早会收到一类很模糊的工单——"详情页挂久了图片就不显示"，查到最后都是签名过期。有效期是业务属性（取决于用户会在详情页停留多久），进配置（`url-expire-seconds`，默认 3600）是唯一合理的位置。

公有和私有的选择也是业务决策：公有 bucket 免签名、零服务器开销，但任何拿到 URL 的人都能永久访问；私有 bucket 每次访问都要服务端签发临时凭证，安全但多一跳。简历、证件照这类敏感内容，没有第二个选项。

### 单测怎么写

单测不碰真实网络，mock 七牛的 SDK 就够了。重点测四件事：mimeType 有没有透传到 `put` 的第五个参数、非 200 时抛不抛异常且关不关流、612 时静不静默、签名 URL 里有没有 `e=` 参数。

"mock SDK 测自己的封装"这个思路本身比用例更重要：测的是**你的代码怎么调 SDK**，而不是 SDK 好不好使。SDK 的行为是七牛的测试范围，你该负责的是"参数传对了没有、异常接住了没有"。

## 四、用第二个厂商验收抽象

阿里云的接入就是把七牛的套路重走一遍，真正有信息量的是差异：

- **客户端形态**：`OSSClient` 一个对象全搞定，不用像七牛那样凑齐 Auth、UploadManager、BucketManager 三件套——API 设计风格的差异，没有高下之分，但抽象层要同时伺候好两种风格。
- **删除幂等**：阿里云删除不存在的对象天然静默成功，约定二在这边是白送的。
- **公有直链**：公有 bucket 直接拼 `https://bucket.endpoint/key`，不用签名，`getUrl` 的两个分支刚好各占一头。

还有一个隐藏差异值得点名：七牛的下载走它管理的连接，阿里云 SDK 的 `OSSObject` 里包着连接。约定三（关流）在阿里云这边不是"最好做"，是"不做就是泄漏"——`OSSObject.getObjectContent()` 拿到的流不关，连接真真切切地不还池。

真正的验收时刻是切换：`obs.provider` 从 qiniu 改成 aliyun，demo 不改一行代码，跑全量测试，全绿。写接口画的是饼，切换无感才算吃到嘴里——很多人止步于前者，然后管它叫抽象。

## 五、业务层：校验、对象键、补偿与删除

业务层按 DDD 四层组织，职责一句话说清：interfaces（REST 控制器，只做协议转换）、application（用例编排，事务边界在这里）、domain（实体和仓储接口，纯业务规则）、infrastructure（仓储实现，MyBatis/JPA）。下面每一段实现都能对应到某一层的职责，留意这个对应关系。

### 校验是业务规则，不是厂商能力

把 `allow-types` 和 `max-size` 放在七牛配置里，是这类系统最常见的错误之一：切换厂商那天，校验跟着旧实现一起下线，新厂商直接裸奔。校验和厂商配置看起来都是"配置"，本质上根本不是一类东西——前者是业务规则（"照片只能是图片"是产品决定，换十家存储都成立），后者是接入参数。

校验策略独立成 [`FileUploadPolicy`](../demo/src/main/java/com/example/demo/application/FileUploadPolicy.java)，按业务场景分了三档（对应简历管理的三类上传入口，换成你的业务就是三档新规则）：

- 通用附件：50MB，常规文件，限制最宽
- 候选人照片：5MB，只收 `image/jpeg`、`image/png`、`image/webp`、`image/gif`
- 候选人简历：10MB，PDF + Word + 常见文档

两个设计点。白名单用精确 MIME 而不是 `image/*`——通配符会把 SVG 放进来，而 SVG 恰好是唯一不能按图片信任的类型（第二章讲过的原因）。白名单在代码里带默认值：漏配置时按安全默认启动，而不是"白名单为空等于全放行"或者"直接拒绝所有"。前者是安全漏洞，后者是可用性事故，都不值得发生在凌晨三点。

另外总大小上限在 Spring 的 multipart 层再拦一道（`spring.servlet.multipart.max-file-size`）——它是第一道闸，超过这个尺寸的请求在进应用层之前就被拒了，堆内存连被撑的机会都没有。应用层校验是第二道闸，负责精细规则。前端预校验是第零道闸，负责用户体验。三道闸各司其职，缺谁都不完整。

### key 必须服务端生成

```java
public String generate(String originalFilename) {
    String ext = extension(originalFilename);   // 清洗后的小写扩展名
    return UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
}
```

key 是对象在存储里的唯一标识，必须服务端生成，这条没有商量余地。让客户端决定 key（比如直接用原始文件名）是三个漏洞打包价：

- **路径穿越**：文件名叫 `../../etc/passwd`，拼进存储路径就是读取任意位置的越权访问。UUID 生成后这个攻击面直接消失——用户传什么名字都无所谓，服务端只取扩展名。
- **覆盖**：两个用户都传 `avatar.png`，后者覆盖前者。UUID 天然不重复。
- **信息泄露**：带业务语义的 key（`张三的简历.pdf`）在 URL 里裸奔，等于把用户信息写在地址栏。

原始文件名只存数据库（候选人表专门有 photo_name、resume_name 两列），下载时靠 Content-Disposition 还原展示，存储层只见 UUID。

### 补偿，而不是分布式事务

上传对象、写数据库，两个动作跨两个系统，经典的一致性问题。重量级方案是分布式事务（2PC、TCC、Saga），但在这个场景里全是杀鸡用牛刀：代价高、引入中间件、调试困难，而失败模式其实很温和。

实际做法是补偿：上传成功、写库失败的 catch 里把刚传的对象删掉。方向上的取舍是：**宁可留孤儿对象，不要留指向空气的数据库记录**。孤儿只是浪费存储费，用户看不见；后者是用户看得见的 404 裂图。至于孤儿，后面清理任务兜底（本章最后一节）。

这个选择背后是个通用原则：**方向性错误选便宜的那个**。孤儿能修（清理任务补删），僵尸记录也能修（人工修数据），但一个花钱、一个丢信任，价格差好几个数量级。

### 一次差点丢数据的顺序问题

示例场景里，候选人更新照片涉及"传新文件、存数据库、删旧文件"三个动作，顺序只有一个正确答案：**先传新 → 保存 → 事务提交后再删旧**。

旧版本是反的：先删旧文件再传新的。这个顺序平时没有任何症状，直到某次新上传失败——旧照片已经物理删除，数据库还指着那个已经不存在的 key。数据就这么没了，备份都救不回来。

```text
正确顺序
传新文件 ──► 写库(指向新key) ──► 事务提交 ──► 删旧对象
              │ 失败 → catch 删掉新对象，旧文件毫发无损

错误顺序
删旧对象 ──► 传新文件，失败
              → 旧文件已没了，库里还指着它：数据丢失
```

事务提交后删旧用的是 `TransactionSynchronization.afterCommit`。这里有个容易被忽略的机制细节，值得单独讲：**afterCommit 回调执行时，事务已经结束**。里面的代码不再受事务保护，抛异常也不会回滚已经提交的数据——它只会沿着调用链往上传播，把一个实际成功的操作变成一个 500 响应。所以 afterCommit 里的异常必须就地接住、记日志、留给兜底逻辑，唯一不能做的事是往上抛。

### 下载：给未来的加密留口子

下载这块已实现 [`FileDownloadService`](../demo/src/main/java/com/example/demo/application/FileDownloadService.java) 接口封装，为下载加密保留扩展位置。📌 仓库当前没有加密下载实现：

```java
public interface FileDownloadService {
    FileDownload download(String key);

    record FileDownload(InputStream stream, String contentType, long contentLength,
                        String contentDisposition, Map<String, String> extraHeaders) {}
}
```

载荷里直接放了完整的响应语义——类型、长度、Disposition、扩展头。为什么不是只给个流？因为加密实现要改的是整体：密文流的长度变了、Content-Type 得换成 octet-stream、预览肯定没了、还得下发密钥的元信息头。只给流的接口，到时候每个字段都得开洞。

将来可以通过装饰器落地，下面代码仅表示设计方向，不是仓库中的现有实现：

```java
public class EncryptedFileDownloadService implements FileDownloadService {
    private final FileDownloadService delegate;   // 装饰直传实现

    @Override
    public FileDownload download(String key) {
        FileDownload raw = delegate.download(key);
        return new FileDownload(
                new CipherInputStream(raw.stream(), cipher),     // AES-GCM 加密流
                "application/octet-stream",                     // 密文不可预览
                raw.contentLength() + 16,                        // 加认证标签长度
                "attachment; filename*=UTF-8''enc-" + ...,       // 强制下载
                Map.of("X-Crypto-Alg", "AES-256-GCM",
                       "X-Crypto-IV", base64Iv));               // 密钥元信息
    }
}
```

`@ConditionalOnProperty` 一切换（`file.download.encoder: encrypted`），Controller 和直传实现一行不动。接口设计做到位，扩展就是把新实现插进 IoC 容器的事。

### 响应头三件套

下载接口的响应头，每个都有明确的对手：

**Content-Disposition 决定浏览器"展示"还是"下载"**。inline 判定不用 `startsWith("image/")`，用精确集合（jpeg/png/gif/webp/bmp）加 PDF——SVG 永远不 inline，这条在第二章就埋了伏笔。文件名用 `filename*=UTF-8''` 走 RFC 5987 编码，中文文件名不会在浏览器下载栏里变成乱码。

**X-Content-Type-Options: nosniff 堵浏览器的内容嗅探**。有些浏览器发现 Content-Type 看起来不可信时会"贴心地"猜一个——就算有人绕过校验声明 `image/png` 实传 HTML，浏览器也只按 PNG 处理，嗅探这条退路被堵死。这是纵深防御的第二层：第一层（校验）被绕过时，它兜底。

**Content-Length 决定下载体验**。有它浏览器才能显示进度条；流式加密场景长度未知时宁可不设（走 chunked），也不要设成 0——那会把"未知"翻译成"空文件"。

### 删除：两级走

直接物理删除有两个问题：误删救不回来；删数据库行和删存储对象是两步，中间进程崩了就出僵尸。所以删除分两级——

**第一级，打标**。请求进来只执行一条 `UPDATE ... SET deleted_at = now() WHERE id = ? AND deleted_at IS NULL`，单条 UPDATE，原子，无对象存储参与。误删在保留期内（默认 7 天）直接改字段就能还原，恢复成本是分钟级的。

**第二级，物理清理**。保留期满，后台任务查出 `deleted_at < now() - 7天` 的行，先删对象、再硬删数据库行——顺序和本章的补偿原则一脉相承：对象删除失败就整行保留，下一轮再试；两边删除都是幂等的，重试没有代价。

两个配套细节，都是实际跑起来才会撞上的：

其一，常规查询一律过滤 `deleted_at IS NULL`，但 `findByIdCard` 故意不过滤。身份证号有唯一索引，软删的行在物理清理前还占着这个号——要是查详情时把它过滤掉，同号的新人插入时撞唯一约束，一个看似无害的查询过滤就变成了业务故障。**软删除最大的成本不在删除本身，在"每个查询都要想清楚要不要看见死数据"**，这个心智负担会伴随系统的余生。

其二，清理任务默认关闭，用 `@ConditionalOnProperty` 门控。理由很实际：默认开启的话，每一套新部署的环境都在跑一个会物理删数据的定时任务，而它的保留期配置可能根本没被人看过。删数据的开关，值一颗显式的配置项。

## 六、前端：上传体验与浏览器边界

前端真正写起来的时间不长，但每个细节都有来头。

**进度条**走 axios 的 `onUploadProgress`，分母用 `e.total`。有个小坑：`e.total` 只在 xhr 的上传事件里可靠，某些 polyfill 场景下它不存在，进度回调里要判空，不然就是"加载中 NaN%"。

**请求取消**用 AbortController，贯穿所有文件相关请求，组件卸载就 abort，拦截器里把 `ERR_CANCELED` 静默掉。不做这个的话，React StrictMode 的双挂载会把取消请求当成错误弹出来——这里给不熟悉 StrictMode 的读者补一句背景：开发模式下 React 会故意把组件挂载-卸载-再挂载各来一次，用来暴露副作用清理不干净的代码。所以"每个 useEffect 都正确清理"不是洁癖，是 StrictMode 的及格线。生产环境它不会重复挂载，但你最好当它会。

**失败重试**：失败的任务保留在列表里，配一个重试按钮，复用原来的任务对象重新发起，不往列表里追加新条目。上传失败是常态（移动网络、超时、413），给用户一条明确的出路比把错误 toast 做得再漂亮都有用。

**预校验**放在 `beforeUpload` 里：50MB 的文件被 Spring 的 413 拒掉之前，浏览器端 0.1 秒就能提示"超限"。用户体验是小事，带宽是真的省——白传 50MB 才知道超限，用户和服务器双输。

**照片预览**用 `URL.createObjectURL` 而不是 `FileReader.readAsDataURL`。后者把整张图读成 base64 字符串塞进 `<img src>`，base64 编码本身膨胀 1.33 倍，而且字符串要完整驻留内存直到被 GC，大图的内存峰值相当难看。`createObjectURL` 只生成一个指向原文件的 blob: 链接，浏览器按需读取，用完 `revokeObjectURL` 释放。blob URL 记得在 useEffect 的 cleanup 里 revoke，而且只 revoke `blob:` 前缀的——编辑态的远程签名 URL 不能误伤，revoke 一个不归你管的 URL 在部分浏览器里是会抛错的。

**Mixed content**：页面 HTTPS、图片 HTTP，浏览器会拦截资源，页面通常只表现为"图不显示"，具体原因可在开发者工具中确认。私有 bucket 的签名 URL 使用存储厂商域名，协议跟随配置，因此本地开发也使用 HTTPS。项目的 Vite 配置读取本地开发证书；⚠️ 证书私钥不得提交到仓库，应提交生成说明或脚本，并将本地证书加入忽略列表。具体准备方式见[快速开始](./getting-started.md#4-准备本地-https-证书)。

## 七、分片上传：什么时候需要

先把项目经验放这：**文件稳定小于 100 MB 时，通常可以先从单文件上传开始**。这不是通用阈值；最终选择应结合并发量、网络质量、请求超时、代理限制和服务端内存压测。示例项目最初 50 MB 上限，只做了单文件直传；后来为了把链路完整跑通，分片和断点续传也落了地。

分片解决三个问题，逐个看：

**内存**。50MB 的文件走 byte[] 一次性读进堆，10 个并发就是 500MB 的瞬时堆压力，GC 一顿抖，OOM 在拐角等着。分片之后任何时刻内存里只有一个 5MB 的分片，并发数终于和内存预算解耦。

**可靠性**。不分片时 50MB 传到 49MB 断网，从头再来。分片后只重传失败的那一片，代价从"整个文件"降到"一片"。

**速度**。单条 TCP 连接的吞吐有上限，分片并发上传（比如 3 条连接同时跑）能把弱网环境的时间砍下来。

文件不大的时候这三个问题一个都碰不到，引入分片的复杂度（会话表、孤儿清理、前端切片逻辑）纯属预付费。但设计先想清楚，需要的那天直接开工。

### 接口：分片是另一组能力，别塞进 ObsStorage

接口层新建并列的 [`ObsMultipartStorage`](../obs-core/src/main/java/com/example/obs/core/ObsMultipartStorage.java)，而不是往 `ObsStorage` 里加五个方法——单文件场景不应该被迫实现五个分片方法，就像不需要事务的系统不应该被迫配置事务管理器。这是接口隔离原则（Interface Segregation）的老规矩：实现类想同时支持两组能力，把两个接口都实现就是了：

```java
public interface ObsMultipartStorage {
    /** 发起分片上传，返回云端 uploadId —— 会话的唯一标识 */
    String initiate(String key, String mimeType);
    /** 上传单个分片，返回分片标识（etag），complete 时要用 */
    String uploadPart(String key, String uploadId, int partNumber, byte[] data);
    /** 列出已上传成功的分片 —— 断点续传的权威数据源 */
    List<ObsPartInfo> listParts(String key, String uploadId);
    /** 通知云端把分片按序拼接成完整对象 */
    void complete(String key, String uploadId, List<ObsPartInfo> parts, String mimeType);
    /** 放弃会话，清理已传分片 */
    void abort(String key, String uploadId);
    /** 单片大小，由实现层决定（阿里云 5MB，七牛 4MB），init 时告知前端 */
    int chunkSize();
}
```

五个方法对应一个完整的生命周期：initiate 拿到 uploadId（云端会话句柄），uploadPart 逐片传（每片返回 etag，云端靠 partNumber + etag 校验完整性），listParts 查询"云端到底收到了哪些片"（续传全靠它），complete 通知云端拼接，abort 主动放弃。多出来的 `chunkSize()` 是被现实逼的：两家厂商对单片最小值的约束不同，这个数该由实现层自己定，业务层只透传给前端。

动手前以为七牛要用 SDK 里的 `ResumeUploader` 加一张分片状态表做桥接，真去看源码才发现另有正路：SDK 暴露了 V2 分片上传的 HTTP 原语（`ApiUploadV2InitUpload` 这一组 Api 类），和阿里云的原生 Multipart API 一样直译就行，桥接层整个省掉了。真正绕一点的只有域名——七牛分片接口不走存储域名，得先查 UC 拿上传域名（公开接口，不用签名），查一次进程内缓存，bucket 迁移域名重启才生效。还有个容易踩的时序差异：mimeType 在阿里云 initiate 时就带上，七牛却在 complete 阶段才生效，接口签名里那个 mimeType 参数就是给后者留的。

### 会话表：续传和清理都靠它

会话状态落一张表：

```sql
CREATE TABLE upload_session (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    fingerprint   VARCHAR(128) NOT NULL,        -- 客户端文件指纹，找回会话的钥匙
    content_hash  CHAR(64) NOT NULL,             -- 完整文件 SHA-256，秒传查重
    upload_id     VARCHAR(255) NOT NULL,          -- 云端会话句柄
    storage_key   VARCHAR(255) NOT NULL,          -- 目标对象 key
    name          VARCHAR(500) NOT NULL,          -- 原始文件名（complete 落业务表用）
    size          BIGINT NOT NULL,                -- 声明的总大小，init 时校验上限
    content_type  VARCHAR(200),                   -- Content-Type，complete 时传给云端
    chunk_size    INT NOT NULL,                   -- 单片大小，init 时从实现层取
    status        VARCHAR(20) NOT NULL,           -- UPLOADING / COMPLETED / ABORTED
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    INDEX idx_upload_session_fp (fingerprint),            -- 指纹找回会话
    INDEX idx_upload_session_status_created (status, created_at)  -- 清理任务扫这张索引
);
```

初版设计里有 total_chunks（分片总数）这一列，落地时删掉了——总数前端自己按 size 和 chunkSize 算，服务端存它没有意义；complete 时真正要核对的是"云端分片总量加起来等于声明的 size"，对不上就拒绝拼接。**总大小在 init 时校验**——别等传完了才发现超限，那时浪费的流量已经发生。**chunk_size 服务端定死**（阿里云 5MB、七牛 4MB，init 响应告知前端），"不信任客户端"这条原则贯穿整条链路。

指纹和内容哈希承担不同职责：指纹用于找回同一次上传会话，完整内容 SHA-256 用于查询已经上传成功的相同文件。两者都落在服务端，不依赖客户端的 localStorage。**元数据落业务表在 complete，不在 init**——init 时对象还没拼完整，业务上这个文件还不存在。会话表是过程记录，业务表只认结果。

### REST 端点与前端实现

四个端点：`POST /api/multipart/init`、`PUT /api/multipart/{sessionId}/parts/{partNumber}`、`POST /api/multipart/{sessionId}/complete`、`DELETE /api/multipart/{sessionId}`。命名没有花活，REST 动词对齐生命周期。两个和初稿不一样的地方值得说：路径上的标识是**服务端会话表的 sessionId 而不是云端 uploadId**——云端的细节不该外露，换厂商时前端无感；初稿里那个独立的 listParts 查询端点砍掉了，**init 响应直接带回 uploadedParts（云端已确认的分片清单）**——续传的第一件事本来就是 init，顺手把进度查回来，省一次往返。

分片本体走 `PUT` + `application/octet-stream` 原始字节，不要用 multipart/form-data 包装——每一片都是独立的小请求，再套一层表单编码纯属浪费。

前端的核心逻辑：

```ts
const CHUNK_SIZE = initResp.chunkSize;              // 服务端说了算

async function uploadWithChunks(file: File, init: InitResp, signal: AbortSignal) {
  const uploaded = new Set(init.uploadedParts.map(p => p.partNumber));
  const total = Math.ceil(file.size / CHUNK_SIZE);
  const tasks = [];
  for (let i = 1; i <= total; i++) {
    if (uploaded.has(i)) continue;                  // 断点：跳过云端已确认的
    tasks.push(() => putPart(init.sessionId, i,
        file.slice((i - 1) * CHUNK_SIZE, i * CHUNK_SIZE), signal));
  }
  await runPool(tasks, 3);                          // 固定并发池
  await complete(init.sessionId);
}
```

两个值得看的点。`file.slice()` 是浏览器原生 API，切的是引用不是拷贝，内存开销可忽略——分片上传在前端几乎免费，昂贵的是在服务端。`runPool` 那个"固定并发池"说起来一句话，实现里藏着好几个决策，值得单独一节。

### 并发控制：三个 worker 抢一个游标

上面那段 `runPool` 是简化骨架，真实现（有删节）长这样：

```ts
const CONCURRENCY = 3;
const PART_MAX_RETRY = 3;

let cursor = 0;                                   // 共享游标：谁空谁取下一片
let failure: unknown = null;                       // 快速失败标记
const inflight = new Map<number, number>();       // partNumber → 已发出字节

const worker = async (): Promise<void> => {
  while (true) {
    if (failure || controls.isPaused() || controls.isCancelled()) return;
    const idx = cursor++;
    if (idx >= missing.length) return;

    const partNumber = missing[idx];
    const blob = file.slice((partNumber - 1) * chunkSize,
        Math.min(partNumber * chunkSize, file.size));
    inflight.set(partNumber, 0);

    for (let attempt = 1; attempt <= PART_MAX_RETRY; attempt++) {
      try {
        await uploadPart(sessionId, partNumber, blob, (loaded) => {
          inflight.set(partNumber, loaded);       // axios onUploadProgress 喂进来的
          reportProgress();
        });
        break;
      } catch (e) {
        if (controls.isPaused() || controls.isCancelled()) return;
        if (attempt === PART_MAX_RETRY) {
          failure = e;
          return;
        }
        await sleep(500 * 2 ** (attempt - 1));    // 500ms → 1s → 2s
      }
    }

    inflight.delete(partNumber);
    doneBytes += blob.size;
    reportProgress();
  }
};

await Promise.all(Array.from(
    { length: Math.min(CONCURRENCY, missing.length) }, () => worker()));
if (failure) throw failure;
```

**游标而不是分堆**。三个 worker 共享一个 cursor，谁空谁取下一片，而不是把分片预先平均分给三个 worker。区别在一片触发重试退避的那一刻：分堆方案里，那个 worker 名下的后续分片全得排队等它；游标方案里，其他两个 worker 照常取片，慢的只是那一片。自适应负载均衡，代价是一个理论上有竞态的 cursor++——但 JS 单线程，worker 之间只有 await 让出执行权，await 之间的同步代码不会被打断，cursor++ 天然原子。

`Math.min(CONCURRENCY, missing.length)` 这处小细节：续传到最后只剩 1 片时，别起 3 个 worker 空转两个。

**failure 是快速失败**。某片三次重试用尽，置 failure 后 return，其他 worker 在循环顶部的检查处陆续收工——正在传的那片传完算数（云端覆盖语义，不浪费），但不再取新片；Promise.all 汇合后统一抛错。一个失败三个停，避免的是"界面已经报错、后台还在偷偷烧流量"。

**暂停不掐请求**。isPaused 命中后 worker 只是不再取新片，在途的那几片让它传完——它们会被云端确认，继续时 init 返回的 uploadedParts 里已经包含它们，一个字节都不浪费。所以"暂停"的真实语义是"停止调度、在途收尾"，状态由外层在 Promise.all 返回后落成 paused。取消（cancel）多一步服务端 abort。掐断在途请求（AbortController）当然也能做，但已发出的字节白传，云端还留一片残缺分片等覆盖重传——不如让它传完。

**进度条**。分子是三项之和：baseBytes（init 时云端已确认的）+ doneBytes（本轮传完的）+ inflight 里各片的 e.loaded（axios onUploadProgress 回调喂进来的实际发出字节）。分母是 file.size，上限 99——complete 没回来之前不敢报 100。外面还套了层 lastPercent 守卫：onUploadProgress 触发频率很高，百分比没变就不 setState，防止渲染风暴。

最后说说服务端：**那边没有锁**。同一会话的三个分片并发打到 uploadPart，各自独立走完三道校验、独立透传云端，谁也不等谁、谁也不挡谁。并发安全的根源不在代码里，在协议里——"云端按分片号覆盖 + complete 以 listParts 为准"这两个语义把 worker 之间的共享状态问题消掉了：三个 worker 之间没有需要保护的状态，唯一的状态在云端。

再往上层看，还有两级并发容易被忽略。**任务级**：上传组件允许多选，每个文件一个任务、各自一套 3-worker 池，全局在途请求数 = 3 × 任务数，目前没有全局信号量。浏览器、协议和厂商限流会影响实际并发，但这些外部约束不应被当成可靠的应用级限流器。尤其在 HTTP/2 多路复用下，应根据真实并发量和厂商配额评估是否增加跨任务的统一信号量。

**连接层**。每个分片请求落到服务端都要借一次数据库连接（requireUploading 里那条 findById 短查询），HikariCP 默认 10 个连接，单条查询毫秒级归还，几个并发任务远打不满。真正被所有分片请求共享的是两个厂商客户端单例——七牛的 okhttp Client、阿里云的 OSS 实例，两家 SDK 都明确线程安全，自动装配里就是单例 Bean，全量请求复用，不需要任何池化。倒是同一分片号被并发重传这种事（重试与续传赛跑）不用操心：云端 last-write-wins，而 complete 拼接用的是服务端自己 listParts 拉回来的清单，客户端手里那个 etag 根本不参与校验——谁最后写进去，就以谁为准。

### 孤儿分片：一个计费问题

init 之后永远不来 complete 的分片，会一直躺在云端计费。这不是边角案例，是分片上传的日常：用户关掉页面、断网、杀进程，会话就悬在那里。两道防线：

- **厂商侧兜底**：配置云厂商的碎片生命周期规则。厂商对 uploadId 有效期的规则可能随产品或接口版本变化；部署时应根据当前 SDK 版本和官方文档复核，代码把会话失效当作可恢复分支处理。
- **应用侧兜底**：定期扫 `status=UPLOADING` 且超过阈值的会话逐个 abort——会话表上那组 `(status, created_at)` 索引就是为这条查询建的。项目里是一个十几行的清理服务挂到 `@Scheduled` 上，由 `file.multipart.cleanup.enabled` 控制开关（默认关闭，和文件清理任务一个思路：确认测试通过后再开）。它把"最多浪费多少"从"直到生命周期规则生效"收紧到"清理周期"。

## 八、断点续传：云端状态优先

分片是机制，续传是状态恢复。状态放两份：

**云端 `listParts` 是唯一权威**。断点续传的第一步永远是问云端"你到底收到了哪些片"——本地记了但网络层失败的、云端没落上的，统统不算数。用本地缓存直接续传，漏掉的片不会被补传，complete 时才发现对不上，前功尽弃。实际落地时更狠一点：本地连分片状态都不记，每次 init 直接问云端，反正就一次查询，换来的是"永远不用考虑本地状态过期"。

**指纹是找回会话的钥匙，内容 SHA-256 是秒传的身份**。指纹仍使用“文件名 + 大小 + 修改时间”，计算快，适合恢复未完成会话；完整 SHA-256 读取文件内容，成本更高，但可以判断两个文件内容是否相同。init 同时携带两者：先按内容哈希查询已完成文件，未命中时再按指纹恢复上传会话。服务端在 complete 后会重新读取云端对象并复算 SHA-256，客户端声明不能直接进入可信索引。

恢复流程一句话讲完：选文件 → 算指纹 → init 带指纹 → 服务端命中 UPLOADING 会话就核对云端 listParts、把已确认分片随响应带回（云端会话过期则作废旧会话、重新发起，对用户无感）→ 前端跳过已传分片续传 → complete。

### 指纹：三元组 + SHA-256

指纹的生成一共二十行：

```ts
async function fileFingerprint(file: File): Promise<string> {
  const raw = `${file.name}:${file.size}:${file.lastModified}`;
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256',
        new TextEncoder().encode(raw));
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  // 非安全上下文降级：Java 的 String.hashCode 同款算法
  let h = 0;
  for (let i = 0; i < raw.length; i++) {
    h = (h * 31 + raw.charCodeAt(i)) | 0;
  }
  return `fallback-${(h >>> 0).toString(16)}`;
}
```

三个字段各有职责：name 把不同文件区分开，size 挡住"同名但内容变了"的大多数情况，lastModified 兜住"同名同大小但改过内容"的尾巴——文件动一次，mtime 必变。哈希结果是定长 64 字符的十六进制，落库 VARCHAR(128) 有富余，init 请求体上再叠一层 `@Size(max = 128)`，长度超标的指纹连门都进不了。

为什么不把原始三元组直接传上来？文件名一列就允许 500 字符，拼上大小和修改时间，原始串最长能奔着七百字去——指纹列、索引、请求体校验全都得跟着膨胀，而 SHA-256 把这些压成死的 64 字符。代价是引入了碰撞概率，但碰撞了会怎样才是关键：最坏结果是续传进别人的会话，complete 时"云端分片总量 ≠ 声明大小"那关会把请求拦下来——代价是失败重来，不是数据错乱。校验链兜底之后，指纹只需要"大概率对"。

`crypto.subtle` 有个必须知道的脾气：只在安全上下文（HTTPS 或 localhost）存在，HTTP 页面下它是 undefined。本地用 HTTP 起前端调试时指纹走不到 SHA-256，所以留了降级分支——31 系数多项式哈希加 `fallback-` 前缀，流程能跑通，续传能力打折。好在这条链路本来就离不开 HTTPS（第六章的 mixed content），生产环境踩不到这个坑。

服务端接住指纹的那一侧更简单，init 的编排拢共四行主干：

```java
public InitResult init(InitCommand command) {
    uploadPolicy.validate(command.contentType(), command.size(), UploadProfile.GENERAL);
    String originalName = FileNameUtil.basename(command.name());
    boolean isImage = FileTypeResolver.isImage(command.contentType(), command.name());

    return sessionRepository.findUploadingByFingerprint(command.fingerprint())
            .flatMap(this::tryResume)
            .orElseGet(() -> createSession(command, originalName, isImage));
}
```

第一行还是那套老校验（类型白名单、大小上限，和单文件上传共用一个 policy），后三行是续传的全部逻辑：按指纹找 UPLOADING 会话，找到就 tryResume 核对云端，找不到就新发起。tryResume 里干的事值得一字一句看——拿会话行里的 storageKey 和 uploadId 去云端 listParts，**核对的是云端此刻的真实状态，不是本地记的进度**。listParts 抛异常说明云端会话已失效（七牛 uploadId 七天过期，撞上是常态不是意外），此时作废本地会话、当新文件重新发起，客户端拿到 resumed=false 无感重来。这一来一回的代价，是"本地会话行"和"云端会话"两份状态永远不会扯皮——每次续传前都重新对账，而不是各说各话。

### 分片校验：前置三道，complete 一道

分片逐个透传云端之前，服务端先过三道校验。分片号必须在 1~10000——两家厂商共同的硬上限，超了云端直接拒。会话必须存在且处于 UPLOADING——COMPLETED 的不再收片，ABORTED 的更不收。分片数据非空、且不超过该会话 init 时定下的 chunkSize——校验基准是会话行里存的值，不是实现层常量，init 之后标准不漂移。这三条防的是同一件事：脏请求打到云端。云厂商按请求次数和碎片存储计费，本地拦一道，省的是真金白银。

```java
public String uploadPart(long sessionId, int partNumber, byte[] data) {
    if (partNumber < 1 || partNumber > MAX_PART_NUMBER) {           // ① 分片号 1~10000
        throw new BizException(BizErrorCode.PART_NUMBER_INVALID);
    }
    UploadSession session = requireUploading(sessionId);             // ② 会话存在且 UPLOADING
    if (data == null || data.length == 0
            || data.length > session.getChunkSize()) {               // ③ 非空且 ≤ 会话单片大小
        throw new BizException(BizErrorCode.PART_DATA_INVALID);
    }
    return multipartStorage.uploadPart(session.getStorageKey(),
            session.getUploadId(), partNumber, data);
}
```

注意方法里没有任何落库动作——分片状态不进数据库，唯一状态在云端。这是刻意为之：本地记分片状态就得考虑"记了但没传上、传了但没记上"两头的脏数据，与其维护两份状态的一致性，不如只留云端一份。

真正的完整性裁决在 complete，而且只信云端：服务端自己 listParts 拉权威清单，前端报了什么、记了什么，都不重要。清单为空，拒——一片没传就来 complete，多半是流程 bug。云端分片总量加起来不等于 init 声明的 size，也拒——少传了片、传错了文件、指纹碰撞，全在这一关现形。两关都过才调云端拼接、落业务表；落库失败则回滚删掉刚拼接好的对象（补偿事务），会话保持 UPLOADING 等客户端重试。

```java
List<ObsPartInfo> parts = multipartStorage.listParts(session.getStorageKey(),
        session.getUploadId());
if (parts.isEmpty()) {
    throw new BizException(BizErrorCode.FILE_EMPTY);
}
long totalSize = parts.stream().mapToLong(ObsPartInfo::size).sum();
if (totalSize != session.getSize()) {
    throw new BizException(BizErrorCode.SIZE_MISMATCH,
            "云端分片总量 " + totalSize + " 与声明大小 " + session.getSize() + " 不一致");
}
```

还有一个细节藏在 complete 方法开头：会话已经是 COMPLETED 就直接查业务表把既有文件信息返回去。complete 的 HTTP 重试、用户手抖连点两下"完成"，都落在这个幂等分支上——第二次调用不会真的再去云端拼一遍。

校验之外还有个语义值得单独记一笔：同一分片号重复上传，云端的行为是覆盖旧片。这直接决定了前端重试不需要任何去重逻辑——哪片失败重发哪片，指数退避（500ms、1s、2s，三次用尽才判失败）。配合覆盖语义，弱网下的自愈基本不用人工干预。

三个工程点，做之前都要想清楚：

✅ **每片带 `Content-MD5` 完整性校验**。前端计算 Base64 MD5，服务端根据请求体重新计算并比较，通过后再传给云厂商。网络链路或代理造成内容变化时只需重传当前分片。MD5 仅用于传输错误检测，文件内容身份使用 SHA-256。

✅ **秒传使用完整内容 SHA-256 查重**。init 命中相同哈希和大小的未删除文件时直接返回已有文件；未命中则正常上传。complete 后服务端从对象存储下载合并结果并复算 SHA-256，校验一致后才把哈希写入秒传索引，避免信任客户端声明。如果下载域名或网络暂不可用，上传保留成功但不写哈希索引，避免可选的秒传校验破坏主上传链路。完整流程见[分片上传与断点续传](./multipart-upload.md#内容秒传)。

**进度条要如实反映"云端已确认"的部分**。本地乐观进度（"这片发出去了"）不能当真——发出去不等于收到，收到不等于成功。把 in-flight 的分片按实际已传字节折算展示，比让用户看着 100% 然后卡在 complete 上转圈诚实得多。

## 排坑速查

这条链路上的坑密度不低，整理成一张表，方便对号入座：

| 现象 | 多半是 |
|---|---|
| 上传被拒，错误码 40002/40003 | 类型不在白名单 / 超过对应场景的大小上限 |
| 上传直接 HTTP 413 | Spring multipart 上限先于应用层校验拦下了 |
| 图片裂开，URL 带着过期时间参数 | 签名 URL 过期，刷新重取或调大有效期 |
| 图片整体不显示，控制台报 mixed content | 页面 HTTPS 图片 HTTP，浏览器静默拦截 |
| 下载下来的文件名是一串 UUID | 走了直链而不是代理下载，Content-Disposition 没兜住 |
| 删了数据，存储里的对象还在 | 软删除保留期内，或者清理任务没开 |
| 分片传完了 complete 却失败 | 分片清单和 listParts 对不上，或 uploadId 过期了 |
| 系统越跑越慢，最后请求超时 | 下载流没关，连接池被抽干（第三章的约定三） |

## 写在最后

这条链路里真正费脑子的从来不是"怎么把文件传上去"——那是文档里就有的部分。费脑子的是那些边界：伪装文件怎么防、两边删除谁先谁后、状态到底信谁、扩展点留在哪一层。

一句话总结我的取舍：**接口定死约定，业务规则留在业务层，方向性错误宁可选便宜的那个**（孤儿对象、模糊失败），因为便宜的错误以后有机会修，贵的错误（丢数据、XSS）没有后悔药。

分片和续传已经跑通了全套：两家厂商的分片原语、服务端会话表、指纹找回、前端并发池、分片 Content-MD5 和基于 SHA-256 的内容秒传。仍可继续扩展全局并发限制、多租户去重范围和 Web Worker 流式哈希。

## 代码导航

- 存储接口：[`ObsStorage`](../obs-core/src/main/java/com/example/obs/core/ObsStorage.java)、[`ObsMultipartStorage`](../obs-core/src/main/java/com/example/obs/core/ObsMultipartStorage.java)
- 厂商自动装配：[`QiniuAutoConfiguration`](../obs-qiniu/src/main/java/com/example/obs/qiniu/QiniuAutoConfiguration.java)、[`AliyunAutoConfiguration`](../obs-aliyun/src/main/java/com/example/obs/aliyun/AliyunAutoConfiguration.java)
- 上传策略：[`FileUploadPolicy`](../demo/src/main/java/com/example/demo/application/FileUploadPolicy.java)
- 分片会话表：[`schema.sql`](../demo/src/main/resources/schema.sql)、[`UploadSessionRecord`](../demo/src/main/java/com/example/demo/infrastructure/persistence/jpa/UploadSessionRecord.java)
- 前端分片上传：[`ChunkUploader.tsx`](../frontend/src/components/ChunkUploader.tsx)

[返回文档索引](./README.md)

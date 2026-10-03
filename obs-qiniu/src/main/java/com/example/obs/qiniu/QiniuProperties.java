package com.example.obs.qiniu;

import com.qiniu.storage.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 七牛云配置属性。
 */
@Data
@ConfigurationProperties(prefix = "qiniu")
public class QiniuProperties {

    private String accessKey;
    private String secretKey;
    private String bucket;
    /** 绑定域名（末尾不带 /） */
    private String domain;
    /** 区域：auto|huadong|huabei|huanan|beimei|xinjiapo */
    private String region = "auto";
    /** 私有下载签名 URL 的有效期（秒），默认 1 小时 */
    private long urlExpireSeconds = 3600;

    /**
     * 将字符串 region 映射到七牛 Region 实例。
     * 新增区域只需扩展 switch，符合开闭原则。
     */
    public Region toRegion() {
        String r = region == null ? "auto" : region.trim().toLowerCase();
        return switch (r) {
            case "huadong" -> Region.huadong();
            case "huabei" -> Region.huabei();
            case "huanan" -> Region.huanan();
            case "beimei" -> Region.beimei();
            case "xinjiapo" -> Region.xinjiapo();
            default -> Region.autoRegion();
        };
    }
}

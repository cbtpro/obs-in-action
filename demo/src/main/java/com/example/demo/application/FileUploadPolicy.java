package com.example.demo.application;

import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * 上传策略（应用层配置）。
 * 类型白名单与大小上限是业务决策，与存储厂商无关，因此由 demo 模块持有（原 qiniu.allow-types/max-size 已迁移至此）。
 * 代码内提供默认值，缺省配置即可启动；可通过 application.yml 的 file.upload.* 覆盖。
 */
@Data
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadPolicy {

    /** 文件中心通用上传规则 */
    private Rule general = new Rule(List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            "application/pdf",
            "application/zip", "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain"), 52428800L);

    /** 候选人照片规则：仅图片，5MB */
    private Rule photo = new Rule(List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"), 5242880L);

    /** 候选人简历规则：文档类，10MB */
    private Rule resume = new Rule(List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "application/zip", "application/x-zip-compressed"), 10485760L);

    /**
     * 按场景校验上传文件：空文件、类型白名单、大小上限。
     *
     * @param file    上传文件（可能为 null，视为空文件）
     * @param profile 上传场景
     * @throws BizException FILE_EMPTY / TYPE_NOT_ALLOWED / SIZE_EXCEEDED
     */
    public void validate(MultipartFile file, UploadProfile profile) {
        if (file == null || file.isEmpty()) {
            throw new BizException(BizErrorCode.FILE_EMPTY);
        }
        validate(file.getContentType(), file.getSize(), profile);
    }

    /**
     * 按场景校验文件元数据（分片上传 init 阶段只有元数据，没有 MultipartFile）。
     *
     * @param contentType 文件类型
     * @param size         文件大小（字节）
     * @param profile      上传场景
     * @throws BizException FILE_EMPTY / TYPE_NOT_ALLOWED / SIZE_EXCEEDED
     */
    public void validate(String contentType, long size, UploadProfile profile) {
        Rule rule = resolve(profile);
        if (size <= 0) {
            throw new BizException(BizErrorCode.FILE_EMPTY);
        }
        if (contentType == null || !rule.getAllowTypes().contains(contentType.trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(BizErrorCode.TYPE_NOT_ALLOWED, "允许的类型: " + rule.getAllowTypes());
        }
        if (size > rule.getMaxSize()) {
            throw new BizException(BizErrorCode.SIZE_EXCEEDED,
                    "大小超出上限 " + (rule.getMaxSize() / 1024 / 1024) + "MB");
        }
    }

    private Rule resolve(UploadProfile profile) {
        return switch (profile) {
            case GENERAL -> general;
            case PHOTO -> photo;
            case RESUME -> resume;
        };
    }

    /**
     * 单场景规则：类型白名单 + 大小上限。
     */
    @Data
    public static class Rule {
        private List<String> allowTypes;
        private long maxSize;

        public Rule() {
        }

        public Rule(List<String> allowTypes, long maxSize) {
            this.allowTypes = allowTypes;
            this.maxSize = maxSize;
        }
    }
}

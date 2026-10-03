package com.example.demo.application;

import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileUploadPolicy 单元测试（不依赖 Spring 上下文，使用代码内默认值）。
 * 覆盖各场景的类型白名单、大小上限、空文件与大小写不敏感。
 */
class FileUploadPolicyTest {

    private final FileUploadPolicy policy = new FileUploadPolicy();

    @Test
    void generalShouldAllowWhitelistedImage() {
        assertDoesNotThrow(() -> policy.validate(
                new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1}), UploadProfile.GENERAL));
    }

    @Test
    void generalShouldAllowDocumentAndZip() {
        assertDoesNotThrow(() -> policy.validate(
                new MockMultipartFile("file", "r.pdf", "application/pdf", new byte[]{1}), UploadProfile.GENERAL));
        assertDoesNotThrow(() -> policy.validate(
                new MockMultipartFile("file", "a.zip", "application/zip", new byte[]{1}), UploadProfile.GENERAL));
    }

    @Test
    void generalShouldRejectSvg() {
        BizException ex = assertThrows(BizException.class, () -> policy.validate(
                new MockMultipartFile("file", "evil.svg", "image/svg+xml", new byte[]{1}), UploadProfile.GENERAL));

        assertEquals(BizErrorCode.TYPE_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void generalShouldRejectHtml() {
        assertThrows(BizException.class, () -> policy.validate(
                new MockMultipartFile("file", "evil.html", "text/html", new byte[]{1}), UploadProfile.GENERAL));
    }

    @Test
    void photoShouldRejectNonImage() {
        BizException ex = assertThrows(BizException.class, () -> policy.validate(
                new MockMultipartFile("file", "r.pdf", "application/pdf", new byte[]{1}), UploadProfile.PHOTO));

        assertEquals(BizErrorCode.TYPE_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void photoShouldRejectOversizedImage() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];

        BizException ex = assertThrows(BizException.class, () -> policy.validate(
                new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig), UploadProfile.PHOTO));

        assertEquals(BizErrorCode.SIZE_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void resumeShouldAllowDocumentButRejectImage() {
        assertDoesNotThrow(() -> policy.validate(
                new MockMultipartFile("file", "r.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        new byte[]{1}), UploadProfile.RESUME));

        assertThrows(BizException.class, () -> policy.validate(
                new MockMultipartFile("file", "p.png", "image/png", new byte[]{1}), UploadProfile.RESUME));
    }

    @Test
    void shouldRejectEmptyFileForAllProfiles() {
        for (UploadProfile profile : UploadProfile.values()) {
            BizException ex = assertThrows(BizException.class, () -> policy.validate(
                    new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]), profile));
            assertEquals(BizErrorCode.FILE_EMPTY, ex.getErrorCode());
        }
    }

    @Test
    void contentTypeShouldBeCaseInsensitive() {
        assertDoesNotThrow(() -> policy.validate(
                new MockMultipartFile("file", "p.jpg", "IMAGE/JPEG", new byte[]{1}), UploadProfile.PHOTO));
    }

    @Test
    void shouldRejectNullContentType() {
        assertThrows(BizException.class, () -> policy.validate(
                new MockMultipartFile("file", "a.jpg", null, new byte[]{1}), UploadProfile.GENERAL));
    }
}

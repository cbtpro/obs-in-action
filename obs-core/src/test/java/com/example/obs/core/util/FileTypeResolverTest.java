package com.example.obs.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeResolver 单元测试。
 */
class FileTypeResolverTest {

    @Test
    void shouldDetectImageByMime() {
        assertTrue(FileTypeResolver.isImage("image/jpeg", "file.txt"));
        assertTrue(FileTypeResolver.isImage("image/png", null));
    }

    @Test
    void shouldDetectImageByExtension() {
        assertTrue(FileTypeResolver.isImage(null, "photo.jpg"));
        assertTrue(FileTypeResolver.isImage("application/octet-stream", "photo.png"));
    }

    @Test
    void shouldReturnFalseForNonImage() {
        assertFalse(FileTypeResolver.isImage("application/pdf", "doc.pdf"));
        assertFalse(FileTypeResolver.isImage(null, "file.txt"));
    }

    @Test
    void shouldCategorizeFiles() {
        assertEquals("image", FileTypeResolver.categorize("photo.jpg"));
        assertEquals("pdf", FileTypeResolver.categorize("doc.pdf"));
        assertEquals("word", FileTypeResolver.categorize("doc.docx"));
        assertEquals("excel", FileTypeResolver.categorize("sheet.xlsx"));
        assertEquals("zip", FileTypeResolver.categorize("archive.zip"));
        assertEquals("text", FileTypeResolver.categorize("readme.md"));
        assertEquals("unknown", FileTypeResolver.categorize("data.bin"));
    }
}

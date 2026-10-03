package com.example.obs.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileNameUtil 单元测试。
 */
class FileNameUtilTest {

    @Test
    void shouldExtractBasenameFromPath() {
        assertEquals("file.txt", FileNameUtil.basename("/path/to/file.txt"));
        assertEquals("file.txt", FileNameUtil.basename("C:\\Users\\file.txt"));
    }

    @Test
    void shouldCleanDangerousChars() {
        assertEquals("file.txt", FileNameUtil.basename("../file.txt"));
    }

    @Test
    void shouldHandleNullAndBlank() {
        assertEquals("", FileNameUtil.basename(null));
        assertEquals("", FileNameUtil.basename(""));
        assertEquals("", FileNameUtil.basename("   "));
    }

    @Test
    void shouldExtractExtension() {
        assertEquals("txt", FileNameUtil.extension("file.txt"));
        assertEquals("jpg", FileNameUtil.extension("photo.JPG"));
        assertEquals("", FileNameUtil.extension("nofile"));
        assertEquals("", FileNameUtil.extension("file."));
    }
}

package com.example.obs.core.util;

import java.util.Set;

/**
 * 文件类型解析工具：判断图片、按扩展名/MIME 分类。
 * 纯 Java，无 Spring 依赖，供各厂商实现 + 业务层共用。
 */
public final class FileTypeResolver {

    private FileTypeResolver() {
    }

    private static final Set<String> IMAGE_MIME = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    private static final Set<String> IMAGE_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"
    );

    /**
     * 判断是否图片类型（按 MIME 或扩展名）。
     */
    public static boolean isImage(String mimeType, String filename) {
        if (mimeType != null && IMAGE_MIME.contains(mimeType.toLowerCase())) {
            return true;
        }
        String ext = FileNameUtil.extension(filename);
        return IMAGE_EXT.contains(ext);
    }

    /**
     * 按扩展名分类文件类型，用于前端展示图标。
     *
     * @return image | pdf | word | excel | zip | text | unknown
     */
    public static String categorize(String filename) {
        String ext = FileNameUtil.extension(filename);
        if (IMAGE_EXT.contains(ext)) return "image";
        if ("pdf".equals(ext)) return "pdf";
        if (Set.of("doc", "docx").contains(ext)) return "word";
        if (Set.of("xls", "xlsx").contains(ext)) return "excel";
        if (Set.of("zip", "rar", "7z", "tar", "gz").contains(ext)) return "zip";
        if (Set.of("txt", "md", "csv", "log").contains(ext)) return "text";
        return "unknown";
    }
}

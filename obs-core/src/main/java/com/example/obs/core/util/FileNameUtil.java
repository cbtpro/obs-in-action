package com.example.obs.core.util;

import java.util.regex.Pattern;

/**
 * 文件名安全工具：防注入、提取扩展名。
 * 纯 Java，无任何依赖，供 obs-core + 业务层共用。
 */
public final class FileNameUtil {

    private FileNameUtil() {
    }

    /** 仅允许字母、数字、下划线、短横、点、中文 */
    private static final Pattern SAFE_NAME = Pattern.compile("[^\\u4e00-\\u9fa5a-zA-Z0-9._-]");

    /**
     * 取 basename（去除路径分隔符），并清理危险字符。
     */
    public static String basename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return SAFE_NAME.matcher(base).replaceAll("").trim();
    }

    /**
     * 提取扩展名（小写，不含点）；无扩展名返回空串。
     */
    public static String extension(String filename) {
        String base = basename(filename);
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        return base.substring(dot + 1).toLowerCase();
    }
}

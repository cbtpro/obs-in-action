package com.example.demo.application;

/**
 * 上传场景枚举：不同场景使用独立的类型白名单与大小上限。
 */
public enum UploadProfile {
    /** 文件中心通用上传 */
    GENERAL,
    /** 候选人照片：仅允许图片 */
    PHOTO,
    /** 候选人简历：仅允许文档类 */
    RESUME
}

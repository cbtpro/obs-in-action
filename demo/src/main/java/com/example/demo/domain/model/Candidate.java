package com.example.demo.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试候选人领域实体。
 * 纯 Java 领域对象，不含 MyBatis-Plus / JPA 注解，由基础设施层持久化。
 */
@Data
@Builder
public class Candidate {

    private Long id;
    /** 姓名 */
    private String name;
    /** 身份证号 */
    private String idCard;
    /** 性别：男 / 女（从身份证自动填充） */
    private String gender;
    /** 年龄（从身份证自动填充） */
    private Integer age;
    /** 照片存储 key */
    private String photoKey;
    /** 照片原始文件名（回显用） */
    private String photoName;
    /** 简历附件存储 key */
    private String resumeKey;
    /** 简历原始文件名（回显用） */
    private String resumeName;
    /** 个人简介 */
    private String bio;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 软删除时间（null 表示未删除，非 null 为已删除待物理清理） */
    private LocalDateTime deletedAt;
}

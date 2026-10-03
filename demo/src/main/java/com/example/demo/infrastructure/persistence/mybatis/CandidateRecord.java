package com.example.demo.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候选人 MyBatis-Plus 实体（基础设施层）。
 */
@Data
@TableName("candidate")
public class CandidateRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("id_card")
    private String idCard;

    private String gender;

    private Integer age;

    @TableField("photo_key")
    private String photoKey;

    @TableField("photo_name")
    private String photoName;

    @TableField("resume_key")
    private String resumeKey;

    @TableField("resume_name")
    private String resumeName;

    private String bio;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 软删除时间（null 表示未删除，非 null 为已删除待物理清理） */
    @TableField(value = "deleted_at")
    private LocalDateTime deletedAt;
}

package com.example.taskservice.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候选人 MyBatis-Plus 实体（task-service 内部使用，共享 candidate 表）。
 * 新增 mock_tag 字段用于标记模拟数据。
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

    private String bio;

    @TableField("mock_tag")
    private String mockTag;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

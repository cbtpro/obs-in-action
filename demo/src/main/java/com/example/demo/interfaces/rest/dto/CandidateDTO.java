package com.example.demo.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 候选人返回 DTO。
 */
@Data
@Builder
public class CandidateDTO {

    private Long id;
    private String name;
    private String idCard;
    private String gender;
    private Integer age;
    /** 照片访问 url */
    private String photoUrl;
    /** 照片原始文件名 */
    private String photoName;
    /** 简历附件访问 url */
    private String resumeUrl;
    /** 简历原始文件名 */
    private String resumeName;
    private String bio;
    private String createdAt;
    private String updatedAt;
}

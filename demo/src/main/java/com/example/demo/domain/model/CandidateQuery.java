package com.example.demo.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * 候选人分页查询条件。
 * 新增过滤条件只需扩展本对象，无需改动方法签名（开闭原则）。
 */
@Value
@Builder
public class CandidateQuery {

    /** 姓名（模糊匹配，可空） */
    String name;

    /** 身份证号（模糊匹配，可空） */
    String idCard;

    /** 性别（精确匹配，可空） */
    String gender;

    /** 分页参数 */
    @Builder.Default
    PageQuery page = PageQuery.of(1, 10);
}

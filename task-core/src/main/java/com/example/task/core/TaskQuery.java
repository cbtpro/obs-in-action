package com.example.task.core;

import lombok.Builder;
import lombok.Value;

/**
 * 任务列表查询条件。
 * 新增过滤条件只需扩展本对象，无需改动方法签名（开闭原则）。
 */
@Value
@Builder
public class TaskQuery {

    /** 关键字（模糊匹配任务 ID / 名称 / 数据标志，可空） */
    String keyword;

    /** 分页参数 */
    @Builder.Default
    PageQuery page = PageQuery.of(1, 10);
}

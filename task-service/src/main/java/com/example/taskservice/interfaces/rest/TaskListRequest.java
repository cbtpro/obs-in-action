package com.example.taskservice.interfaces.rest;

import lombok.Data;

/**
 * 任务分页查询请求。
 * 查询条件通过请求体传递，与候选人列表接口保持一致的 POST 查询模式。
 */
@Data
public class TaskListRequest {

    /** 关键字（模糊匹配任务 ID / 名称 / 数据标志） */
    private String keyword;

    /** 页码，从 1 起（空则取默认值） */
    private Integer page;

    /** 每页条数（空则取默认值） */
    private Integer pageSize;
}

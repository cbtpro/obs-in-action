package com.example.demo.interfaces.rest.dto;

/**
 * 候选人分页查询请求。
 * 查询条件（含身份证号等敏感信息）通过请求体传递，避免出现在 URL 与访问日志中。
 *
 * @param name     姓名（模糊匹配，可空）
 * @param idCard   身份证号（模糊匹配，可空）
 * @param gender   性别（精确匹配，可空）
 * @param page     页码，从 1 起（空则取默认值）
 * @param pageSize 每页条数（空则取默认值）
 */
public record CandidateListRequest(
        String name,
        String idCard,
        String gender,
        Integer page,
        Integer pageSize) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 页码（null 安全，空则返回默认值） */
    public int safePage() {
        return page != null ? page : DEFAULT_PAGE;
    }

    /** 每页条数（null 安全，空则返回默认值） */
    public int safePageSize() {
        return pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
    }
}

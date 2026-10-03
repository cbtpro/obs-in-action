package com.example.task.core;

/**
 * 分页请求值对象。
 * 构造时规范化参数：页码从 1 起，每页大小限制在 [1, MAX_PAGE_SIZE]。
 */
public record PageQuery(int page, int pageSize) {

    /** 每页最大条数，防止一次拉取过多 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    public static PageQuery of(int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new PageQuery(p, size);
    }

    /** 跳过的行数 */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}

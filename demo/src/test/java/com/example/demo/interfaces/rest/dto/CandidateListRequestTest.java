package com.example.demo.interfaces.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CandidateListRequest 默认值兜底测试。
 * 前端可能省略分页字段（空查询），请求体反序列化后为 null。
 */
class CandidateListRequestTest {

    @Test
    void shouldFallbackToDefaultsWhenPaginationAbsent() {
        CandidateListRequest request = new CandidateListRequest(null, null, null, null, null);

        assertEquals(1, request.safePage());
        assertEquals(10, request.safePageSize());
    }

    @Test
    void shouldUseProvidedPagination() {
        CandidateListRequest request = new CandidateListRequest("张", "110", "男", 3, 20);

        assertEquals("张", request.name());
        assertEquals("110", request.idCard());
        assertEquals("男", request.gender());
        assertEquals(3, request.safePage());
        assertEquals(20, request.safePageSize());
    }
}

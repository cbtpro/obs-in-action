package com.example.task.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageQuery 参数规范化测试。
 */
class PageQueryTest {

    @Test
    void shouldNormalizeNegativePageToOne() {
        assertEquals(1, PageQuery.of(-5, 10).page());
        assertEquals(1, PageQuery.of(0, 10).page());
    }

    @Test
    void shouldNormalizeInvalidPageSizeToDefault() {
        assertEquals(10, PageQuery.of(1, 0).pageSize());
        assertEquals(10, PageQuery.of(1, -3).pageSize());
    }

    @Test
    void shouldCapPageSizeAtMax() {
        assertEquals(100, PageQuery.of(1, 9999).pageSize());
        assertEquals(100, PageQuery.of(1, 100).pageSize());
        assertEquals(50, PageQuery.of(1, 50).pageSize());
    }

    @Test
    void shouldComputeOffsetCorrectly() {
        assertEquals(0, PageQuery.of(1, 10).offset());
        assertEquals(10, PageQuery.of(2, 10).offset());
        assertEquals(200, PageQuery.of(3, 100).offset());
    }
}

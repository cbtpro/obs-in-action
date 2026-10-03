package com.example.demo.domain.model;

import java.util.List;

/**
 * 分页结果值对象（通用）。
 *
 * @param total   满足条件的总条数
 * @param records 当前页数据
 */
public record PageResult<T>(long total, List<T> records) {
}

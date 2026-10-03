package com.example.demo.interfaces.rest.dto;

import java.util.List;

/**
 * 通用分页响应 DTO。
 *
 * @param total   满足条件的总条数
 * @param records 当前页数据
 */
public record PageDTO<T>(long total, List<T> records) {
}

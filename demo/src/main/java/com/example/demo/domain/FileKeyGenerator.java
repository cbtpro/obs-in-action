package com.example.demo.domain;

/**
 * 存储 key 生成策略接口。
 * 业务层通过此接口生成对象存储的 key，与具体生成算法解耦。
 */
public interface FileKeyGenerator {

    /**
     * 根据原始文件名生成存储 key。
     */
    String generate(String originalFilename);
}

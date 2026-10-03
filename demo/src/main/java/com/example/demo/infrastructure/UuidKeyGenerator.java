package com.example.demo.infrastructure;

import com.example.demo.domain.FileKeyGenerator;
import com.example.obs.core.util.FileNameUtil;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 默认 key 生成策略：{uuid}.{ext}
 * 原始文件名由 DB 持久化，key 无需编码原名。
 */
@Component
public class UuidKeyGenerator implements FileKeyGenerator {

    @Override
    public String generate(String originalFilename) {
        String ext = FileNameUtil.extension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return ext.isEmpty() ? uuid : uuid + "." + ext;
    }
}

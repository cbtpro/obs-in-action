package com.example.obs.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.example.obs.core.ObsMultipartStorage;
import com.example.obs.core.ObsStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 阿里云 OSS 自动配置。
 * 当 obs.provider=aliyun 时激活，注册 OSS 客户端 + ObsStorage 实现 Bean。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AliyunProperties.class)
@ConditionalOnProperty(name = "obs.provider", havingValue = "aliyun")
public class AliyunAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OSS ossClient(AliyunProperties props) {
        return new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
    }

    @Bean
    @ConditionalOnMissingBean(ObsStorage.class)
    public ObsStorage obsStorage(AliyunProperties props, OSS ossClient) {
        log.info("激活阿里云 OSS ObsStorage 实现");
        return new AliyunObsStorage(props, ossClient);
    }

    @Bean
    @ConditionalOnMissingBean(ObsMultipartStorage.class)
    public ObsMultipartStorage multipartStorage(AliyunProperties props, OSS ossClient) {
        log.info("激活阿里云 OSS ObsMultipartStorage 实现");
        return new AliyunMultipartStorage(props, ossClient);
    }
}

package com.example.obs.qiniu;

import com.example.obs.core.ObsMultipartStorage;
import com.example.obs.core.ObsStorage;
import com.qiniu.http.Client;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 七牛云自动配置。
 * 当 obs.provider=qiniu（或未配置时默认）自动激活，注册七牛客户端 + ObsStorage 实现 Bean。
 * 业务层只需 @Autowired ObsStorage，不感知底层实现。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(QiniuProperties.class)
@ConditionalOnProperty(name = "obs.provider", havingValue = "qiniu", matchIfMissing = true)
public class QiniuAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Auth qiniuAuth(QiniuProperties props) {
        return Auth.create(props.getAccessKey(), props.getSecretKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public com.qiniu.storage.Configuration qiniuConfiguration(QiniuProperties props) {
        return new com.qiniu.storage.Configuration(props.toRegion());
    }

    @Bean
    @ConditionalOnMissingBean
    public UploadManager uploadManager(com.qiniu.storage.Configuration configuration) {
        return new UploadManager(configuration);
    }

    @Bean
    @ConditionalOnMissingBean
    public BucketManager bucketManager(Auth auth, com.qiniu.storage.Configuration configuration) {
        return new BucketManager(auth, configuration);
    }

    @Bean
    @ConditionalOnMissingBean(ObsStorage.class)
    public ObsStorage obsStorage(QiniuProperties props, Auth auth,
                                  UploadManager uploadManager, BucketManager bucketManager) {
        log.info("激活七牛云 ObsStorage 实现");
        return new QiniuObsStorage(props, auth, uploadManager, bucketManager);
    }

    /** SDK 底层 HTTP 客户端：分片 API 原语（ApiUploadV2*）与域名查询共用 */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public Client qiniuHttpClient(com.qiniu.storage.Configuration configuration) {
        return new Client(configuration);
    }

    @Bean
    @ConditionalOnMissingBean(ObsMultipartStorage.class)
    public ObsMultipartStorage multipartStorage(QiniuProperties props, Auth auth, Client client) {
        log.info("激活七牛云 ObsMultipartStorage 实现");
        return new QiniuMultipartStorage(props, auth, client);
    }
}

package com.educheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百度智能云 AI 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "baidu-cloud")
public class BaiduCloudConfig {

    private String appId;
    private String apiKey;
    private String secretKey;

    private Face face = new Face();

    @Data
    public static class Face {
        /** 人脸对比通过阈值（0-100），推荐 80 */
        private int scoreThreshold = 80;
    }
}

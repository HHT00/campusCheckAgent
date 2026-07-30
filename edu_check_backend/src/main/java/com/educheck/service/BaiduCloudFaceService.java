package com.educheck.service;

import com.educheck.config.BaiduCloudConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 百度智能云人脸对比服务 V3
 * 文档: https://cloud.baidu.com/doc/FACE/s/akn38w8a6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaiduCloudFaceService {

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String FACE_MATCH_URL = "https://aip.baidubce.com/rest/2.0/face/v3/match";

    private final BaiduCloudConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 缓存的 access_token */
    private String accessToken;
    /** token 过期时间戳（毫秒） */
    private long tokenExpireTime;

    /**
     * 获取有效的 access_token（自动缓存，过期自动刷新）
     */
    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        try {
            String url = TOKEN_URL + "?grant_type=client_credentials"
                    + "&client_id=" + config.getApiKey()
                    + "&client_secret=" + config.getSecretKey();

            String response = restTemplate.postForObject(url, null, String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("error")) {
                log.error("百度云 access_token 获取失败: {} - {}",
                        json.get("error").asText(), json.get("error_description").asText());
                return null;
            }

            accessToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt(); // 单位秒，默认 2592000 (30天)
            // 提前 1 天刷新
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 86400) * 1000L;
            log.info("百度云 access_token 刷新成功，有效期 {} 秒", expiresIn);
            return accessToken;
        } catch (Exception e) {
            log.error("调用百度云获取 access_token 异常", e);
            return null;
        }
    }

    /**
     * 人脸对比 V3
     *
     * @param image1Base64 第一张图片的 base64 数据（不含 data:image 前缀）
     * @param image2Base64 第二张图片的 base64 数据
     * @return 对比结果（分数 0-100，分数越高越相似），返回 null 表示调用失败
     */
    public FaceMatchResult compare(String image1Base64, String image2Base64) {
        try {
            String token = getAccessToken();
            if (token == null) {
                return FaceMatchResult.failure("百度云 access_token 获取失败");
            }

            // 构建请求体
            Map<String, Object> face1 = new HashMap<>();
            face1.put("image", image1Base64);
            face1.put("image_type", "BASE64");
            face1.put("face_type", "LIVE");
            face1.put("quality_control", "LOW");

            Map<String, Object> face2 = new HashMap<>();
            face2.put("image", image2Base64);
            face2.put("image_type", "BASE64");
            face2.put("face_type", "LIVE");
            face2.put("quality_control", "LOW");

            Object[] body = new Object[]{face1, face2};

            String url = FACE_MATCH_URL + "?access_token=" + token;
            String response = restTemplate.postForObject(url, body, String.class);
            JsonNode json = objectMapper.readTree(response);

            int errorCode = json.get("error_code").asInt();
            if (errorCode != 0) {
                String errorMsg = json.get("error_msg").asText();
                log.warn("百度云人脸对比失败: error_code={}, error_msg={}", errorCode, errorMsg);
                return FaceMatchResult.failure(errorMsg);
            }

            JsonNode result = json.get("result");
            double score = result.get("score").asDouble();

            log.info("百度云人脸对比完成，相似度: {}", score);
            return FaceMatchResult.success(score, score >= config.getFace().getScoreThreshold());
        } catch (Exception e) {
            log.error("调用百度云人脸对比 API 异常", e);
            return FaceMatchResult.failure("人脸识别服务调用异常");
        }
    }

    /**
     * 人脸对比结果
     */
    public record FaceMatchResult(
            boolean success,
            double score,
            boolean passed,
            String errorMsg
    ) {
        public static FaceMatchResult success(double score, boolean passed) {
            return new FaceMatchResult(true, score, passed, null);
        }

        public static FaceMatchResult failure(String errorMsg) {
            return new FaceMatchResult(false, 0, false, errorMsg);
        }
    }
}

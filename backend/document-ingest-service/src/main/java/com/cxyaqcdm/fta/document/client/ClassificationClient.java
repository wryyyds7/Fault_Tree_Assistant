package com.cxyaqcdm.fta.document.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ClassificationClient {

    private final RestTemplate restTemplate;

    @Value("${classification.service.url:http://localhost:8002}")
    private String classificationServiceUrl;

    public ClassificationClient() {
        this.restTemplate = new RestTemplate();
    }

    public static class ClassificationResult {
        public String sourceType;
        public double confidence;
        public String reasoning;
        public String method;
        public double credibilityWeight;

        public String getSourceType() {
            return sourceType;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getMethod() {
            return method;
        }

        public double getCredibilityWeight() {
            return credibilityWeight;
        }
    }

    public ClassificationResult classifyDocument(String documentName, String content) {
        try {
            log.info("========================================================");
            log.info("★★☆ 开始调用分类服务 ☆★★");
            log.info("文档名称: {}", documentName);
            log.info("内容预览长度: {}", content != null ? content.length() : 0);
            if (content != null) {
                log.info("内容预览前100字符: {}", content.substring(0, Math.min(100, content.length())));
            }
            log.info("服务URL: {}", classificationServiceUrl);
            log.info("========================================================");

            String url = classificationServiceUrl + "/api/v1/document/classify";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("documentName", documentName);
            requestBody.put("content", content);
            requestBody.put("usePreMatching", false);  // 直接使用LLM，不使用预匹配
            requestBody.put("contentPreviewLength", 800);

            log.info("请求体: {}", requestBody);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            log.info("响应状态: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                log.info("响应体: {}", body);

                ClassificationResult result = new ClassificationResult();
                result.sourceType = (String) body.get("sourceType");
                result.confidence = ((Number) body.get("confidence")).doubleValue();
                result.reasoning = (String) body.get("reasoning");
                result.method = (String) body.get("method");
                result.credibilityWeight = ((Number) body.get("credibilityWeight")).doubleValue();
                log.info("★★☆ 分类成功 ☆★★");
                log.info("sourceType={}, confidence={}, method={}",
                        result.sourceType, result.confidence, result.method);
                log.info("========================================================");
                return result;
            }

            log.warn("Classification returned unexpected status: {}", response.getStatusCode());
            return createDefaultResult();

        } catch (Exception e) {
            log.error("★★☆ 分类失败 ☆★★", e);
            log.error("Failed to call classification service: {}", e.getMessage());
            log.info("========================================================");
            return createDefaultResult();
        }
    }

    private ClassificationResult createDefaultResult() {
        ClassificationResult result = new ClassificationResult();
        result.sourceType = "unknown";
        result.confidence = 0.3;
        result.reasoning = "分类服务不可用，使用默认分类";
        result.method = "default";
        result.credibilityWeight = 0.5;
        return result;
    }
}

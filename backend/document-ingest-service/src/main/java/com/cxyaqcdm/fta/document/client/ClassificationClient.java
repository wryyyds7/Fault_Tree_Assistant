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
            String url = classificationServiceUrl + "/api/v1/document/classify";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("documentName", documentName);
            requestBody.put("content", content);
            requestBody.put("usePreMatching", true);
            requestBody.put("contentPreviewLength", 800);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                ClassificationResult result = new ClassificationResult();
                result.sourceType = (String) body.get("sourceType");
                result.confidence = ((Number) body.get("confidence")).doubleValue();
                result.reasoning = (String) body.get("reasoning");
                result.method = (String) body.get("method");
                result.credibilityWeight = ((Number) body.get("credibilityWeight")).doubleValue();
                log.info("Document classified: sourceType={}, confidence={}, method={}",
                        result.sourceType, result.confidence, result.method);
                return result;
            }

            log.warn("Classification returned unexpected status: {}", response.getStatusCode());
            return createDefaultResult();

        } catch (Exception e) {
            log.error("Failed to call classification service: {}", e.getMessage());
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

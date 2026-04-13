package com.cxyaqcdm.fta.common.client;

import com.cxyaqcdm.fta.common.entity.OperationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class LogServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${log.service.url:http://localhost:8095}")
    private String logServiceUrl;

    @Async("logTaskExecutor")
    public void writeLog(OperationLog operationLog) {
        try {
            String url = logServiceUrl + "/api/v1/log/write";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<OperationLog> request = new HttpEntity<>(operationLog, headers);
            restTemplate.postForEntity(url, request, Void.class);
            log.debug("Log sent to log-service successfully");
        } catch (Exception e) {
            log.error("Failed to send log to log-service: {}", e.getMessage());
        }
    }
}

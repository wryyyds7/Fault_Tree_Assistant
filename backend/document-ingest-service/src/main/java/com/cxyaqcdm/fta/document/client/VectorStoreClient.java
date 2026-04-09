package com.cxyaqcdm.fta.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "vector-store-service", url = "http://vector-store-service:8090")
public interface VectorStoreClient {
    @PostMapping("/api/v1/vector/process")
    void processDocument(@RequestBody Map<String, Object> request);
}
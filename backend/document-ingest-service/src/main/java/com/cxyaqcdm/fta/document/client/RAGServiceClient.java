package com.cxyaqcdm.fta.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "rag-generation-service", url = "${rag.service.url:http://localhost:8000}")
public interface RAGServiceClient {

    @PostMapping("/api/v1/rag/sync-vector")
    Map<String, Object> syncVectorsToChroma(@RequestBody Map<String, Object> request);

    @DeleteMapping("/api/v1/rag/vectors")
    Map<String, Object> deleteVectors(@RequestParam("docId") String docId, @RequestParam("userId") String userId);

    @GetMapping("/health")
    String health();
}
package com.cxyaqcdm.fta.document.client;

import com.cxyaqcdm.fta.common.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "vector-store-service", configuration = FeignClientConfig.class)
public interface VectorStoreClient {
    @PostMapping(value = "/api/v1/vector/documents", consumes = "application/json", produces = "application/json")
    Map<String, Object> createDocumentMetadata(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/vector/process")
    void processDocument(@RequestBody Map<String, Object> request);

    @DeleteMapping("/api/v1/vector/documents/{docId}")
    void deleteDocumentMetadata(@PathVariable("docId") String docId);

    @GetMapping("/api/v1/vector/documents")
    List<Map<String, Object>> getAllDocumentMetadata(@RequestParam(required = false) String userId);

    @GetMapping("/api/v1/vector/documents/{docId}")
    Map<String, Object> getDocumentMetadataByDocId(@PathVariable("docId") String docId);
}
package com.cxyaqcdm.fta.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "knowledge-graph-service", url = "${knowledge-graph.service.url:http://localhost:8092}")
public interface KnowledgeGraphClient {

    @DeleteMapping("/api/v1/kg/user-document")
    void deleteUserDocumentKnowledge(@RequestParam("userId") String userId, @RequestParam("docId") String docId);

    @PostMapping("/api/v1/kg/enrich")
    void enrichKnowledge(@RequestBody Map<String, Object> causalPattern);

    @GetMapping("/api/v1/kg/data")
    Map<String, Object> getKnowledgeGraphData(@RequestParam(value = "userId", required = false) String userId);
}

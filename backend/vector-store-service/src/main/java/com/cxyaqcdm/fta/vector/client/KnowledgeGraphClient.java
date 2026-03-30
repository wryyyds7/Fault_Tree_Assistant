package com.cxyaqcdm.fta.vector.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "knowledge-graph-service", url = "http://knowledge-graph-service:8082")
public interface KnowledgeGraphClient {
    @PostMapping("/api/v1/kg/enrich")
    void enrichKnowledge(@RequestBody Map<String, Object> causalPattern);
}
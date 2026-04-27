package com.cxyaqcdm.fta.vector.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "knowledge-graph-service", url = "${knowledge-graph.service.url:http://localhost:8092}")
public interface KnowledgeGraphClient {
    @PutMapping("/api/v1/kg/enrich")
    void enrichKnowledge(@RequestBody Map<String, Object> causalPattern);
}
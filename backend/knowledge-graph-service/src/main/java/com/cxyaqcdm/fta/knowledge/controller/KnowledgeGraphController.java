package com.cxyaqcdm.fta.knowledge.controller;

import com.cxyaqcdm.fta.knowledge.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/kg")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    @PostMapping("/query-template")
    public ResponseEntity<Map<String, Object>> queryTemplate(@RequestBody Map<String, String> request) {
        String topEvent = request.get("topEvent");
        String equipmentType = request.get("equipmentType");
        Map<String, Object> template = knowledgeGraphService.queryTemplate(topEvent, equipmentType);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getKnowledgeGraphData(
            @RequestParam(required = false) String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        Map<String, Object> data = knowledgeGraphService.getKnowledgeGraphData(userId);
        return ResponseEntity.ok(data);
    }

    @PutMapping("/enrich")
    public ResponseEntity<Void> enrichKnowledge(@RequestBody Map<String, Object> causalPattern) {
        knowledgeGraphService.enrichKnowledge(causalPattern);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/user-document")
    public ResponseEntity<Void> deleteUserDocumentKnowledge(
            @RequestParam String userId,
            @RequestParam String docId) {
        knowledgeGraphService.deleteUserDocumentKnowledge(userId, docId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/initialize")
    public ResponseEntity<Void> initializeOntology() {
        knowledgeGraphService.initializeOntology();
        return ResponseEntity.ok().build();
    }

    @PutMapping("/save-event")
    public ResponseEntity<Void> saveEvent(@RequestBody Map<String, Object> eventData) {
        knowledgeGraphService.saveEvent(eventData);
        return ResponseEntity.ok().build();
    }
}

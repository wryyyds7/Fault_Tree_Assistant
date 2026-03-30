package com.cxyaqcdm.fta.vector.controller;

import com.cxyaqcdm.fta.vector.entity.DocumentMetadata;
import com.cxyaqcdm.fta.vector.entity.ParagraphMetadata;
import com.cxyaqcdm.fta.vector.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vector")
@RequiredArgsConstructor
@Slf4j
public class VectorStoreController {

    private final VectorStoreService vectorStoreService;

    // 文档元数据相关接口
    @PostMapping("/documents")
    public ResponseEntity<DocumentMetadata> createDocumentMetadata(@RequestBody Map<String, Object> request) {
        String docId = (String) request.get("docId");
        String fileName = (String) request.get("fileName");
        String fileType = (String) request.get("fileType");
        Integer pageCount = (Integer) request.get("pageCount");
        
        DocumentMetadata metadata = vectorStoreService.createDocumentMetadata(docId, fileName, fileType, pageCount);
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<DocumentMetadata> getDocumentMetadata(@PathVariable String docId) {
        DocumentMetadata metadata = vectorStoreService.getDocumentMetadata(docId);
        return metadata != null ? ResponseEntity.ok(metadata) : ResponseEntity.notFound().build();
    }

    @PutMapping("/documents/{docId}")
    public ResponseEntity<Void> updateDocumentMetadata(@PathVariable String docId, @RequestBody DocumentMetadata metadata) {
        metadata.setDocId(docId);
        vectorStoreService.updateDocumentMetadata(metadata);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> deleteDocumentMetadata(@PathVariable String docId) {
        vectorStoreService.deleteDocumentMetadata(docId);
        return ResponseEntity.noContent().build();
    }

    // 段落元数据相关接口
    @PostMapping("/documents/{docId}/paragraphs")
    public ResponseEntity<List<ParagraphMetadata>> createParagraphMetadata(@PathVariable String docId, @RequestBody List<Map<String, Object>> paragraphs) {
        List<ParagraphMetadata> metadataList = vectorStoreService.createParagraphMetadata(docId, paragraphs);
        return ResponseEntity.ok(metadataList);
    }

    @GetMapping("/documents/{docId}/paragraphs")
    public ResponseEntity<List<ParagraphMetadata>> getParagraphMetadataByDocId(@PathVariable String docId) {
        List<ParagraphMetadata> metadataList = vectorStoreService.getParagraphMetadataByDocId(docId);
        return ResponseEntity.ok(metadataList);
    }

    @GetMapping("/paragraphs/{paragraphId}")
    public ResponseEntity<ParagraphMetadata> getParagraphMetadataByParagraphId(@PathVariable String paragraphId) {
        ParagraphMetadata metadata = vectorStoreService.getParagraphMetadataByParagraphId(paragraphId);
        return metadata != null ? ResponseEntity.ok(metadata) : ResponseEntity.notFound().build();
    }

    // 向量相关接口
    @PostMapping("/documents/{docId}/vectors")
    public ResponseEntity<Void> generateVectors(@PathVariable String docId) {
        List<ParagraphMetadata> paragraphs = vectorStoreService.getParagraphMetadataByDocId(docId);
        vectorStoreService.generateVectors(docId, paragraphs);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{docId}/vectors")
    public ResponseEntity<List<Map<String, Object>>> getVectorsByDocId(@PathVariable String docId) {
        // 这里应该返回向量数据，但为了简化，返回空列表
        return ResponseEntity.ok(vectorStoreService.getVectorsByDocId(docId).stream()
                .map(vector -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("vectorId", vector.getVectorId());
                    map.put("paragraphId", vector.getParagraphId());
                    map.put("vectorDimension", vector.getVectorDimension());
                    return map;
                })
                .toList());
    }

    @PostMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchSimilarVectors(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int topK = (int) request.getOrDefault("topK", 5);
        
        List<Map<String, Object>> results = vectorStoreService.searchSimilarVectors(query, topK);
        return ResponseEntity.ok(results);
    }

    // 完整流程接口
    @PostMapping("/process")
    public ResponseEntity<Void> processDocument(@RequestBody Map<String, Object> request) {
        String docId = (String) request.get("docId");
        String fileName = (String) request.get("fileName");
        String fileType = (String) request.get("fileType");
        Integer pageCount = (Integer) request.get("pageCount");
        List<Map<String, Object>> paragraphs = (List<Map<String, Object>>) request.get("paragraphs");
        String sourceType = (String) request.getOrDefault("sourceType", "unknown");
        Double credibilityWeight = request.get("credibilityWeight") != null ?
            ((Number) request.get("credibilityWeight")).doubleValue() : null;
        String equipmentType = (String) request.get("equipmentType");
        Boolean persistToKnowledgeBase = (Boolean) request.getOrDefault("persistToKnowledgeBase", false);

        vectorStoreService.processDocument(docId, fileName, fileType, pageCount, paragraphs,
                sourceType, credibilityWeight, equipmentType, persistToKnowledgeBase);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/paragraphs/{paragraphId}/evidence")
    public ResponseEntity<Map<String, Object>> getParagraphEvidence(@PathVariable String paragraphId) {
        Map<String, Object> evidence = vectorStoreService.getParagraphEvidence(paragraphId);
        return evidence != null ? ResponseEntity.ok(evidence) : ResponseEntity.notFound().build();
    }

    @PostMapping("/search-with-evidence")
    public ResponseEntity<List<Map<String, Object>>> searchWithEvidence(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int topK = (int) request.getOrDefault("topK", 5);
        List<Map<String, Object>> results = vectorStoreService.searchWithEvidence(query, topK);
        return ResponseEntity.ok(results);
    }
}
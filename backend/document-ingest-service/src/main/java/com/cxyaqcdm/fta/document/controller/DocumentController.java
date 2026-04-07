package com.cxyaqcdm.fta.document.controller;

import com.cxyaqcdm.fta.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", required = false, defaultValue = "unknown") String sourceType,
            @RequestParam(value = "equipmentType", required = false) String equipmentType,
            @RequestParam(value = "persistToKnowledgeBase", required = false, defaultValue = "false") Boolean persistToKnowledgeBase) {
        try {
            Map<String, Object> result = documentService.uploadDocument(file, sourceType, equipmentType, persistToKnowledgeBase);
            String status = (String) result.get("status");
            if ("error".equals(status)) {
                log.error("Document upload failed: {}", result.get("message"));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Document upload exception: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    @GetMapping("/{docId}/content")
    public ResponseEntity<Map<String, Object>> getDocumentContent(@PathVariable String docId) {
        Map<String, Object> result = documentService.getDocumentContent(docId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{docId}/paragraphs")
    public ResponseEntity<List<Map<String, Object>>> getDocumentParagraphs(@PathVariable String docId) {
        List<Map<String, Object>> paragraphs = documentService.getDocumentParagraphs(docId);
        return ResponseEntity.ok(paragraphs);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllDocuments() {
        List<Map<String, Object>> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(documents);
    }
}

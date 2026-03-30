package com.cxyaqcdm.fta.document.controller;

import com.cxyaqcdm.fta.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", required = false, defaultValue = "unknown") String sourceType,
            @RequestParam(value = "equipmentType", required = false) String equipmentType,
            @RequestParam(value = "persistToKnowledgeBase", required = false, defaultValue = "false") Boolean persistToKnowledgeBase) {
        Map<String, Object> result = documentService.uploadDocument(file, sourceType, equipmentType, persistToKnowledgeBase);
        return ResponseEntity.ok(result);
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
}

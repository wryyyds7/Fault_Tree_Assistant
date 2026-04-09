package com.cxyaqcdm.fta.document.controller;

import com.cxyaqcdm.fta.common.context.UserContext;
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

    private String getCurrentUserId() {
        UserContext userContext = UserContext.getCurrentUser();
        if (userContext != null) {
            return userContext.getUserId();
        }
        return null;
    }

    private String getCurrentUserRole() {
        UserContext userContext = UserContext.getCurrentUser();
        if (userContext != null) {
            return userContext.getRole();
        }
        return "USER";
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", required = false, defaultValue = "unknown") String sourceType,
            @RequestParam(value = "equipmentType", required = false) String equipmentType,
            @RequestParam(value = "persistToKnowledgeBase", required = false, defaultValue = "false") Boolean persistToKnowledgeBase,
            @RequestParam(value = "userId", required = false) String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                userId = getCurrentUserId();
            }
            Map<String, Object> result = documentService.uploadDocument(file, sourceType, equipmentType, persistToKnowledgeBase, userId);
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
    public ResponseEntity<Map<String, Object>> getDocumentContent(
            @PathVariable String docId,
            @RequestParam(value = "userId", required = false) String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = getCurrentUserId();
        }
        Map<String, Object> result = documentService.getDocumentContent(docId, userId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{docId}/paragraphs")
    public ResponseEntity<List<Map<String, Object>>> getDocumentParagraphs(
            @PathVariable String docId,
            @RequestParam(value = "userId", required = false) String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = getCurrentUserId();
        }
        List<Map<String, Object>> paragraphs = documentService.getDocumentParagraphs(docId, userId);
        return ResponseEntity.ok(paragraphs);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllDocuments(
            @RequestParam(value = "userId", required = false) String userId) {
        String role = getCurrentUserRole();
        String currentUserId = getCurrentUserId();

        if ("ADMIN".equals(role)) {
            userId = null;
        } else if (userId == null || userId.isEmpty()) {
            userId = currentUserId;
        }

        List<Map<String, Object>> documents = documentService.getAllDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable String docId,
            @RequestParam(value = "userId", required = false) String userId) {
        log.info("Delete document request: docId={}, userId={}", docId, userId);
        if (userId == null || userId.isEmpty()) {
            userId = getCurrentUserId();
        }
        try {
            boolean success = documentService.deleteDocument(docId, userId);
            if (success) {
                log.info("Document deleted successfully: {}", docId);
                Map<String, Object> result = new HashMap<>();
                result.put("status", "success");
                result.put("message", "文档删除成功");
                return ResponseEntity.ok(result);
            } else {
                log.warn("Document not found or delete failed: {}", docId);
                Map<String, Object> result = new HashMap<>();
                result.put("status", "error");
                result.put("message", "文档不存在或删除失败");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            }
        } catch (Exception e) {
            log.error("Delete document exception: docId={}, error={}", docId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
}

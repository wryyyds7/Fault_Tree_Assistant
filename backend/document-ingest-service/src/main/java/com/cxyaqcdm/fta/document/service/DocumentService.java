package com.cxyaqcdm.fta.document.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DocumentService {
    Map<String, Object> uploadDocument(MultipartFile file, String sourceType, String equipmentType, Boolean persistToKnowledgeBase, String userId);
    void processDocument(Map<String, Object> message);
    Map<String, Object> getDocumentContent(String docId, String userId);
    List<Map<String, Object>> getDocumentParagraphs(String docId, String userId);
    List<Map<String, Object>> getAllDocuments(String userId);
    boolean deleteDocument(String docId, String userId);
}

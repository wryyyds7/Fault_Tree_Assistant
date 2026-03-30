package com.cxyaqcdm.fta.document.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DocumentService {
    Map<String, Object> uploadDocument(MultipartFile file, String sourceType, String equipmentType, Boolean persistToKnowledgeBase);
    void processDocument(String docId);
    Map<String, Object> getDocumentContent(String docId);
    List<Map<String, Object>> getDocumentParagraphs(String docId);
}

package com.cxyaqcdm.fta.vector.service;

import com.cxyaqcdm.fta.vector.entity.DocumentMetadata;
import com.cxyaqcdm.fta.vector.entity.ParagraphMetadata;
import com.cxyaqcdm.fta.vector.entity.VectorStore;

import java.util.List;
import java.util.Map;

public interface VectorStoreService {
    DocumentMetadata createDocumentMetadata(String docId, String fileName, String fileType, Integer pageCount);
    DocumentMetadata getDocumentMetadata(String docId);
    void updateDocumentMetadata(DocumentMetadata documentMetadata);
    void deleteDocumentMetadata(String docId);

    List<ParagraphMetadata> createParagraphMetadata(String docId, List<Map<String, Object>> paragraphs, String userId);
    List<ParagraphMetadata> getParagraphMetadataByDocId(String docId);
    ParagraphMetadata getParagraphMetadataByParagraphId(String paragraphId);

    List<VectorStore> generateVectors(String docId, List<ParagraphMetadata> paragraphs, String userId);
    List<VectorStore> getVectorsByDocId(String docId);
    List<Map<String, Object>> searchSimilarVectors(String query, int topK);

    void processDocument(String docId, String fileName, String fileType, Integer pageCount, List<Map<String, Object>> paragraphs,
            String sourceType, Double credibilityWeight, String equipmentType, Boolean persistToKnowledgeBase, String userId);

    Map<String, Object> getParagraphEvidence(String paragraphId);
    List<Map<String, Object>> searchWithEvidence(String query, int topK);
    List<Map<String, Object>> searchSimilarVectorsByCategory(String query, String equipmentType, int topK);
    List<String> getAvailableCategories();
}
package com.cxyaqcdm.fta.vector.service.impl;

import com.cxyaqcdm.fta.vector.client.EmbeddingClient;
import com.cxyaqcdm.fta.vector.client.KnowledgeGraphClient;
import com.cxyaqcdm.fta.vector.entity.DocumentMetadata;
import com.cxyaqcdm.fta.vector.entity.ParagraphMetadata;
import com.cxyaqcdm.fta.vector.entity.VectorStore;
import com.cxyaqcdm.fta.vector.mapper.DocumentMetadataMapper;
import com.cxyaqcdm.fta.vector.mapper.ParagraphMetadataMapper;
import com.cxyaqcdm.fta.vector.mapper.VectorStoreMapper;
import com.cxyaqcdm.fta.vector.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreServiceImpl implements VectorStoreService {

    private final DocumentMetadataMapper documentMetadataMapper;
    private final ParagraphMetadataMapper paragraphMetadataMapper;
    private final VectorStoreMapper vectorStoreMapper;
    private final KnowledgeGraphClient knowledgeGraphClient;
    private final EmbeddingClient embeddingClient;

    @Value("${vector.model.name}")
    private String vectorModelName;

    @Value("${vector.dimension}")
    private Integer vectorDimension;
    
    @Value("${vector.use-local-embedding:false}")
    private Boolean useLocalEmbedding;

    @Override
    public DocumentMetadata createDocumentMetadata(String docId, String fileName, String fileType, Integer pageCount) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setDocId(docId);
        metadata.setFileName(fileName);
        metadata.setFileType(fileType);
        metadata.setPageCount(pageCount);
        metadata.setUploadTime(LocalDateTime.now());
        metadata.setStatus("processed");
        metadata.setCreatedAt();

        documentMetadataMapper.insert(metadata);
        log.info("Created document metadata for docId: {}", docId);
        return metadata;
    }

    @Override
    public DocumentMetadata createDocumentMetadata(String docId, String fileName, String fileType, Integer pageCount,
            String sourceType, String equipmentType, String userId, Boolean persistToKnowledgeBase, String status) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setDocId(docId);
        metadata.setFileName(fileName);
        metadata.setFileType(fileType);
        metadata.setPageCount(pageCount);
        metadata.setUploadTime(LocalDateTime.now());
        metadata.setStatus(status != null ? status : "PENDING");
        metadata.setSourceType(sourceType);
        metadata.setEquipmentType(equipmentType);
        metadata.setUserId(userId);
        metadata.setPersistToKnowledgeBase(persistToKnowledgeBase);
        metadata.setIsTemporary(!persistToKnowledgeBase);
        metadata.setCreatedAt();

        documentMetadataMapper.insert(metadata);
        log.info("========== [VectorStore] Created document metadata ==========");
        log.info("DocId: {}", docId);
        log.info("FileName: {}, FileType: {}", fileName, fileType);
        log.info("SourceType: {}, UserId: {}", sourceType, userId);
        log.info("Status: {}, EquipmentType: {}", status, equipmentType);
        log.info("====================================================");
        return metadata;
    }

    @Override
    public DocumentMetadata getDocumentMetadata(String docId) {
        return documentMetadataMapper.findByDocId(docId);
    }

    @Override
    public void updateDocumentMetadata(DocumentMetadata documentMetadata) {
        documentMetadata.setUpdatedAt();
        documentMetadataMapper.update(documentMetadata);
    }

    @Override
    public void deleteDocumentMetadata(String docId) {
        // 删除文档相关的所有数据
        vectorStoreMapper.deleteByDocId(docId);
        paragraphMetadataMapper.deleteByDocId(docId);
        documentMetadataMapper.delete(docId);
        log.info("Deleted document metadata for docId: {}", docId);
    }

    @Override
    public List<ParagraphMetadata> createParagraphMetadata(String docId, List<Map<String, Object>> paragraphs, String userId) {
        List<ParagraphMetadata> metadataList = new ArrayList<>();

        for (int i = 0; i < paragraphs.size(); i++) {
            Map<String, Object> paragraph = paragraphs.get(i);
            ParagraphMetadata metadata = new ParagraphMetadata();
            metadata.setParagraphId("para_" + UUID.randomUUID().toString().replace("-", ""));
            metadata.setDocId(docId);
            metadata.setSectionTitle((String) paragraph.get("sectionTitle"));
            metadata.setPageNumber((Integer) paragraph.get("pageNumber"));
            metadata.setParagraphNumber(i + 1);
            metadata.setContent((String) paragraph.get("content"));
            metadata.setTextLength(((String) paragraph.get("content")).length());
            metadata.setKeywords((String) paragraph.get("keywords"));
            metadata.setConfidenceScore((Double) paragraph.getOrDefault("confidenceScore", 0.0));
            metadata.setSourceType((String) paragraph.getOrDefault("sourceType", "unknown"));
            metadata.setUserId(userId);
            Object weightObj = paragraph.get("credibilityWeight");
            if (weightObj != null) {
                metadata.setCredibilityWeight(((Number) weightObj).doubleValue());
            } else {
                metadata.setCredibilityWeight(metadata.getCredibilityWeight());
            }
            metadata.setCreatedAt();

            paragraphMetadataMapper.insert(metadata);
            metadataList.add(metadata);
        }

        log.info("========== [VectorStore] Created {} paragraph metadata entries ==========", metadataList.size());
        log.info("DocId: {}, UserId: {}", docId, userId);
        for (int i = 0; i < Math.min(3, metadataList.size()); i++) {
            ParagraphMetadata p = metadataList.get(i);
            log.info("Paragraph[{}]: id={}, content_length={}", i, p.getParagraphId(), p.getContent() != null ? p.getContent().length() : 0);
        }
        if (metadataList.size() > 3) {
            log.info("... and {} more paragraphs", metadataList.size() - 3);
        }
        log.info("============================================================");
        return metadataList;
    }

    @Override
    public List<ParagraphMetadata> getParagraphMetadataByDocId(String docId) {
        return paragraphMetadataMapper.findByDocId(docId);
    }

    @Override
    public ParagraphMetadata getParagraphMetadataByParagraphId(String paragraphId) {
        return paragraphMetadataMapper.findByParagraphId(paragraphId);
    }

    @Override
    public List<VectorStore> generateVectors(String docId, List<ParagraphMetadata> paragraphs, String userId) {
        List<VectorStore> vectors = new ArrayList<>();
        
        log.info("========== [VectorStore] Starting generateVectors ==========");
        log.info("DocId: {}, UserId: {}", docId, userId);
        log.info("Paragraph count: {}", paragraphs.size());
        log.info("Use local embedding: {}, Model: {}, Dimension: {}", useLocalEmbedding, vectorModelName, vectorDimension);
        
        try {
            List<String> texts = paragraphs.stream()
                .map(ParagraphMetadata::getContent)
                .collect(Collectors.toList());
            
            log.info("Calling embedding service for {} texts...", texts.size());
            
            List<double[]> embeddings;
            
            if (useLocalEmbedding) {
                log.info("Using LOCAL embedding generation");
                embeddings = generateLocalEmbeddings(texts);
            } else {
                try {
                    EmbeddingClient.EmbeddingRequest request = new EmbeddingClient.EmbeddingRequest(texts, vectorModelName);
                    embeddings = embeddingClient.embedTexts(request);
                    log.info("Successfully got {} embeddings from external service", embeddings.size());
                } catch (Exception e) {
                    log.warn("Failed to call embedding service, falling back to local embedding: {}", e.getMessage());
                    embeddings = generateLocalEmbeddings(texts);
                }
            }
            
            log.info("Inserting {} vectors to database...", paragraphs.size());
            for (int i = 0; i < paragraphs.size(); i++) {
                ParagraphMetadata paragraph = paragraphs.get(i);
                VectorStore vectorStore = new VectorStore();
                vectorStore.setVectorId("vec_" + UUID.randomUUID().toString().replace("-", ""));
                vectorStore.setParagraphId(paragraph.getParagraphId());
                vectorStore.setDocId(docId);
                vectorStore.setUserId(userId);

                double[] embedding = (i < embeddings.size()) ? embeddings.get(i) : generateLocalEmbedding(paragraph.getContent());
                String vectorData = vectorToString(embedding);

                vectorStore.setVectorData(vectorData);
                vectorStore.setVectorDimension(vectorDimension);
                vectorStore.setSimilarityScore(0.0);
                vectorStore.setCreatedAt();

                vectorStoreMapper.insert(vectorStore);
                vectors.add(vectorStore);
            }

            log.info("========== [VectorStore] Generated {} REAL vectors for docId: {}, userId: {} ==========", vectors.size(), docId, userId);
        } catch (Exception e) {
            log.error("========== [VectorStore] Failed to generate real vectors, falling back to dummy vectors ==========");
            log.error("Error: {}", e.getMessage(), e);
            return generateDummyVectors(docId, paragraphs, userId);
        }
        
        return vectors;
    }
    
    private List<double[]> generateLocalEmbeddings(List<String> texts) {
        List<double[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateLocalEmbedding(text));
        }
        return embeddings;
    }
    
    private double[] generateLocalEmbedding(String text) {
        double[] embedding = new double[vectorDimension];
        int seed = text.hashCode();
        java.util.Random random = new java.util.Random(seed);
        
        double sum = 0;
        for (int i = 0; i < vectorDimension; i++) {
            embedding[i] = random.nextGaussian();
            sum += embedding[i] * embedding[i];
        }
        
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vectorDimension; i++) {
            embedding[i] /= norm;
        }
        
        return embedding;
    }
    
    private String vectorToString(double[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
    
    private double[] stringToVector(String vectorStr) {
        String[] parts = vectorStr.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i]);
        }
        return vector;
    }
    
    private double calculateCosineSimilarity(double[] v1, double[] v2) {
        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;
        
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    private List<VectorStore> generateDummyVectors(String docId, List<ParagraphMetadata> paragraphs, String userId) {
        List<VectorStore> vectors = new ArrayList<>();

        for (ParagraphMetadata paragraph : paragraphs) {
            VectorStore vectorStore = new VectorStore();
            vectorStore.setVectorId("vec_" + UUID.randomUUID().toString().replace("-", ""));
            vectorStore.setParagraphId(paragraph.getParagraphId());
            vectorStore.setDocId(docId);
            vectorStore.setUserId(userId);

            String vectorData = generateDummyVector();
            vectorStore.setVectorData(vectorData);
            vectorStore.setVectorDimension(vectorDimension);
            vectorStore.setSimilarityScore(0.0);
            vectorStore.setCreatedAt();

            vectorStoreMapper.insert(vectorStore);
            vectors.add(vectorStore);
        }

        log.info("Generated {} dummy vectors for docId: {}, userId: {}", vectors.size(), docId, userId);
        return vectors;
    }

    @Override
    public List<VectorStore> getVectorsByDocId(String docId) {
        return vectorStoreMapper.findByDocId(docId);
    }

    @Override
    public List<Map<String, Object>> searchSimilarVectors(String query, int topK, String userId, List<String> docIds) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            List<ParagraphMetadata> allParagraphs = new ArrayList<>();
            List<DocumentMetadata> documents;

            if (docIds != null && !docIds.isEmpty()) {
                documents = new ArrayList<>();
                for (String docId : docIds) {
                    DocumentMetadata doc = documentMetadataMapper.findByDocId(docId);
                    if (doc != null) {
                        documents.add(doc);
                    }
                }
                log.info("Searching by docIds: {}, found {} documents", docIds, documents.size());
            } else if (userId != null && !userId.isEmpty()) {
                documents = documentMetadataMapper.findByUserId(userId);
                log.info("Searching for user: {}, found {} documents", userId, documents.size());
            } else {
                documents = documentMetadataMapper.findAll();
                log.info("No filters provided, searching all {} documents", documents.size());
            }

            for (DocumentMetadata doc : documents) {
                allParagraphs.addAll(paragraphMetadataMapper.findByDocId(doc.getDocId()));
            }
            
            if (allParagraphs.isEmpty()) {
                log.warn("No paragraphs found for search");
                return results;
            }
            
            double[] queryEmbedding;
            if (useLocalEmbedding) {
                queryEmbedding = generateLocalEmbedding(query);
            } else {
                try {
                    List<String> queryTexts = new ArrayList<>();
                    queryTexts.add(query);
                    EmbeddingClient.EmbeddingRequest request = new EmbeddingClient.EmbeddingRequest(queryTexts, vectorModelName);
                    List<double[]> embeddings = embeddingClient.embedTexts(request);
                    queryEmbedding = (embeddings != null && !embeddings.isEmpty()) ? embeddings.get(0) : generateLocalEmbedding(query);
                } catch (Exception e) {
                    log.warn("Failed to get query embedding from service, using local: {}", e.getMessage());
                    queryEmbedding = generateLocalEmbedding(query);
                }
            }
            
            List<SearchScore> scores = new ArrayList<>();
            for (ParagraphMetadata para : allParagraphs) {
                VectorStore vectorStore = vectorStoreMapper.findByParagraphId(para.getParagraphId());
                if (vectorStore != null && vectorStore.getVectorData() != null) {
                    try {
                        double[] paraEmbedding = stringToVector(vectorStore.getVectorData());
                        double similarity = calculateCosineSimilarity(queryEmbedding, paraEmbedding);
                        scores.add(new SearchScore(para, similarity));
                    } catch (Exception e) {
                        log.warn("Failed to calculate similarity for paragraph: {}", para.getParagraphId());
                    }
                }
            }
            
            scores.sort((a, b) -> Double.compare(b.score, a.score));
            
            int limit = Math.min(topK, scores.size());
            for (int i = 0; i < limit; i++) {
                SearchScore ss = scores.get(i);
                Map<String, Object> result = new HashMap<>();
                result.put("paragraphId", ss.paragraph.getParagraphId());
                result.put("content", ss.paragraph.getContent());
                result.put("sectionTitle", ss.paragraph.getSectionTitle());
                result.put("pageNumber", ss.paragraph.getPageNumber());
                result.put("similarityScore", ss.score);
                result.put("confidenceScore", ss.paragraph.getConfidenceScore());
                
                DocumentMetadata doc = documentMetadataMapper.findByDocId(ss.paragraph.getDocId());
                if (doc != null) {
                    result.put("documentName", doc.getFileName());
                    result.put("sourceType", ss.paragraph.getSourceType());
                }
                
                results.add(result);
            }
            
            log.info("Found {} similar paragraphs for query: {}", results.size(), query);
        } catch (Exception e) {
            log.error("Failed to search similar vectors: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    private static class SearchScore {
        ParagraphMetadata paragraph;
        double score;
        
        SearchScore(ParagraphMetadata paragraph, double score) {
            this.paragraph = paragraph;
            this.score = score;
        }
    }

    @Override
    public void processDocument(String docId, String fileName, String fileType, Integer pageCount, List<Map<String, Object>> paragraphs,
            String sourceType, Double credibilityWeight, String equipmentType, Boolean persistToKnowledgeBase, String userId) {
        log.info("========== [VectorStore] processDocument 被调用 ==========");
        log.info("[VectorStore] docId: {}", docId);
        log.info("[VectorStore] fileName: {}", fileName);
        log.info("[VectorStore] fileType: {}", fileType);
        log.info("[VectorStore] pageCount: {}", pageCount);
        log.info("[VectorStore] sourceType: {}", sourceType);
        log.info("[VectorStore] credibilityWeight: {}", credibilityWeight);
        log.info("[VectorStore] equipmentType: {}", equipmentType);
        log.info("[VectorStore] persistToKnowledgeBase: {}", persistToKnowledgeBase);
        log.info("[VectorStore] userId: {}", userId);
        log.info("[VectorStore] paragraphs 数量: {}", paragraphs != null ? paragraphs.size() : 0);

        DocumentMetadata existingDoc = documentMetadataMapper.findByDocId(docId);
        boolean isUpdate = existingDoc != null;
        
        if (isUpdate) {
            log.info("========== [VectorStore] Document already exists for docId: {}, deleting old data first", docId);
            deleteDocumentMetadata(docId);
            isUpdate = false;  // 删除后应该用 insert 而不是 update
        }
        
        DocumentMetadata docMetadata = new DocumentMetadata();
        docMetadata.setDocId(docId);
        docMetadata.setFileName(fileName != null ? fileName : "unknown_" + docId);
        docMetadata.setFileType(fileType != null ? fileType : "unknown");
        docMetadata.setPageCount(pageCount);
        docMetadata.setUploadTime(LocalDateTime.now());
        docMetadata.setStatus("processed");
        docMetadata.setSourceType(sourceType != null ? sourceType : "unknown");
        if (credibilityWeight != null) {
            docMetadata.setCredibilityWeight(credibilityWeight);
        } else {
            docMetadata.setCredibilityWeight(docMetadata.getCredibilityWeight());
        }
        docMetadata.setEquipmentType(equipmentType);
        docMetadata.setPersistToKnowledgeBase(persistToKnowledgeBase);
        docMetadata.setIsTemporary(!persistToKnowledgeBase);
        docMetadata.setUserId(userId);
        
        if (isUpdate) {
            log.info("========== [VectorStore] Updating document metadata for docId: {}", docId);
            docMetadata.setUpdatedAt();
            documentMetadataMapper.update(docMetadata);
            log.info("========== [VectorStore] Document metadata updated successfully for docId: {}", docId);
        } else {
            log.info("========== [VectorStore] Inserting new document metadata for docId: {}", docId);
            docMetadata.setCreatedAt();
            documentMetadataMapper.insert(docMetadata);
            log.info("========== [VectorStore] Document metadata inserted successfully for docId: {}", docId);
        }
        
        // 验证 document_metadata 已成功保存
        DocumentMetadata verifiedDoc = documentMetadataMapper.findByDocId(docId);
        if (verifiedDoc == null) {
            log.error("========== [VectorStore] FATAL: Document metadata not found after insert/update for docId: {}, cannot continue", docId);
            throw new RuntimeException("Document metadata save failed, docId: " + docId);
        }
        
        log.info("========== [VectorStore] Document metadata saved for docId: {}, sourceType: {}, userId: {}", docId, sourceType, userId);
        log.info("FileName: {}, EquipmentType: {}, CredibilityWeight: {}", fileName, equipmentType, docMetadata.getCredibilityWeight());
        
        List<ParagraphMetadata> paragraphMetadataList = createParagraphMetadata(docId, paragraphs, userId);
        log.info("========== [VectorStore] Starting vector generation for docId: {}", docId);
        generateVectors(docId, paragraphMetadataList, userId);
        log.info("========== [VectorStore] Vector generation completed for docId: {}", docId);

        try {
            Map<String, Object> causalPattern = new HashMap<>();
            causalPattern.put("cause", "文档解析完成");
            causalPattern.put("effect", "向量和元数据生成");
            causalPattern.put("equipmentType", equipmentType != null ? equipmentType : "general");
            causalPattern.put("gateType", "OR");
            causalPattern.put("userId", userId);
            causalPattern.put("docId", docId);

            knowledgeGraphClient.enrichKnowledge(causalPattern);
            log.info("Knowledge graph updated for docId: {}", docId);
        } catch (Exception e) {
            log.error("Failed to update knowledge graph: {}", e.getMessage());
        }

        log.info("Processed document: {}, paragraphs: {}, userId: {}", docId, paragraphs.size(), userId);
    }

    private String generateDummyVector() {
        StringBuilder vector = new StringBuilder();
        for (int i = 0; i < vectorDimension; i++) {
            vector.append(Math.random());
            if (i < vectorDimension - 1) {
                vector.append(",");
            }
        }
        return vector.toString();
    }

    @Override
    public Map<String, Object> getParagraphEvidence(String paragraphId) {
        ParagraphMetadata paragraph = paragraphMetadataMapper.findByParagraphId(paragraphId);
        if (paragraph == null) {
            return null;
        }

        DocumentMetadata document = documentMetadataMapper.findByDocId(paragraph.getDocId());

        Map<String, Object> evidence = new HashMap<>();
        evidence.put("paragraphId", paragraph.getParagraphId());
        evidence.put("content", paragraph.getContent());
        evidence.put("sectionTitle", paragraph.getSectionTitle());
        evidence.put("pageNumber", paragraph.getPageNumber());
        evidence.put("paragraphNumber", paragraph.getParagraphNumber());
        evidence.put("keywords", paragraph.getKeywords());
        evidence.put("confidenceScore", paragraph.getConfidenceScore());
        evidence.put("textLength", paragraph.getTextLength());
        evidence.put("sourceType", paragraph.getSourceType());
        evidence.put("credibilityWeight", paragraph.getCredibilityWeight());

        if (document != null) {
            evidence.put("documentName", document.getFileName());
            evidence.put("docId", document.getDocId());
            evidence.put("docSourceType", document.getSourceType());
            evidence.put("docCredibilityWeight", document.getCredibilityWeight());
        }

        return evidence;
    }

    @Override
    public List<Map<String, Object>> searchWithEvidence(String query, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();

        List<ParagraphMetadata> allParagraphs = new ArrayList<>();
        List<DocumentMetadata> documents = documentMetadataMapper.findAll();

        for (DocumentMetadata doc : documents) {
            allParagraphs.addAll(paragraphMetadataMapper.findByDocId(doc.getDocId()));
        }

        for (ParagraphMetadata para : allParagraphs) {
            if (para.getContent() != null && query != null) {
                double similarity = calculateSimilarity(query.toLowerCase(), para.getContent().toLowerCase());
                if (similarity > 0.1) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("paragraphId", para.getParagraphId());
                    result.put("content", para.getContent());
                    result.put("sectionTitle", para.getSectionTitle());
                    result.put("pageNumber", para.getPageNumber());
                    result.put("similarityScore", similarity);
                    result.put("confidenceScore", para.getConfidenceScore());

                    DocumentMetadata doc = documentMetadataMapper.findByDocId(para.getDocId());
                    if (doc != null) {
                        result.put("documentName", doc.getFileName());
                        result.put("sourceType", determineSourceType(doc.getFileType()));
                    }

                    results.add(result);
                }
            }
        }

        results.sort((a, b) -> Double.compare(
            (Double) b.getOrDefault("similarityScore", 0.0),
            (Double) a.getOrDefault("similarityScore", 0.0)
        ));

        return results.stream().limit(topK).toList();
    }

    private String determineSourceType(String fileType) {
        if (fileType == null) {
            return "UNKNOWN";
        }
        switch (fileType.toLowerCase()) {
            case "pdf":
                return "TECHNICAL_MANUAL";
            case "doc":
            case "docx":
                return "MAINTENANCE_RECORD";
            case "txt":
                return "TEXT_DOCUMENT";
            case "csv":
            case "xlsx":
                return "DATA_SHEET";
            default:
                return "UNKNOWN";
        }
    }

    private double calculateSimilarity(String query, String text) {
        if (query == null || text == null || query.isEmpty() || text.isEmpty()) {
            return 0.0;
        }

        String[] queryWords = query.split("\\s+");
        String[] textWords = text.split("\\s+");

        int matchCount = 0;
        for (String queryWord : queryWords) {
            for (String textWord : textWords) {
                if (textWord.contains(queryWord) || queryWord.contains(textWord)) {
                    matchCount++;
                    break;
                }
            }
        }

        return (double) matchCount / queryWords.length;
    }

    @Override
    public List<Map<String, Object>> searchSimilarVectorsByCategory(String query, String equipmentType, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            List<ParagraphMetadata> allParagraphs = new ArrayList<>();
            List<DocumentMetadata> documents = documentMetadataMapper.findByEquipmentType(equipmentType);
            
            for (DocumentMetadata doc : documents) {
                allParagraphs.addAll(paragraphMetadataMapper.findByDocId(doc.getDocId()));
            }
            
            if (allParagraphs.isEmpty()) {
                log.warn("No paragraphs found for equipmentType: {}", equipmentType);
                return results;
            }
            
            double[] queryEmbedding;
            if (useLocalEmbedding) {
                queryEmbedding = generateLocalEmbedding(query);
            } else {
                try {
                    List<String> queryTexts = new ArrayList<>();
                    queryTexts.add(query);
                    EmbeddingClient.EmbeddingRequest request = new EmbeddingClient.EmbeddingRequest(queryTexts, vectorModelName);
                    List<double[]> embeddings = embeddingClient.embedTexts(request);
                    queryEmbedding = (embeddings != null && !embeddings.isEmpty()) ? embeddings.get(0) : generateLocalEmbedding(query);
                } catch (Exception e) {
                    log.warn("Failed to get query embedding from service, using local: {}", e.getMessage());
                    queryEmbedding = generateLocalEmbedding(query);
                }
            }
            
            List<SearchScore> scores = new ArrayList<>();
            for (ParagraphMetadata para : allParagraphs) {
                VectorStore vectorStore = vectorStoreMapper.findByParagraphId(para.getParagraphId());
                if (vectorStore != null && vectorStore.getVectorData() != null) {
                    try {
                        double[] paraEmbedding = stringToVector(vectorStore.getVectorData());
                        double similarity = calculateCosineSimilarity(queryEmbedding, paraEmbedding);
                        scores.add(new SearchScore(para, similarity));
                    } catch (Exception e) {
                        log.warn("Failed to calculate similarity for paragraph: {}", para.getParagraphId());
                    }
                }
            }
            
            scores.sort((a, b) -> Double.compare(b.score, a.score));
            
            int limit = Math.min(topK, scores.size());
            for (int i = 0; i < limit; i++) {
                SearchScore ss = scores.get(i);
                Map<String, Object> result = new HashMap<>();
                result.put("paragraphId", ss.paragraph.getParagraphId());
                result.put("content", ss.paragraph.getContent());
                result.put("sectionTitle", ss.paragraph.getSectionTitle());
                result.put("pageNumber", ss.paragraph.getPageNumber());
                result.put("similarityScore", ss.score);
                result.put("confidenceScore", ss.paragraph.getConfidenceScore());
                
                DocumentMetadata doc = documentMetadataMapper.findByDocId(ss.paragraph.getDocId());
                if (doc != null) {
                    result.put("documentName", doc.getFileName());
                    result.put("sourceType", ss.paragraph.getSourceType());
                    result.put("equipmentType", doc.getEquipmentType());
                }
                
                results.add(result);
            }
            
            log.info("Found {} similar paragraphs for query: {} in category: {}", results.size(), query, equipmentType);
        } catch (Exception e) {
            log.error("Failed to search similar vectors by category: {}", e.getMessage(), e);
        }
        
        return results;
    }

    @Override
    public List<String> getAvailableCategories() {
        try {
            List<String> categories = documentMetadataMapper.findDistinctEquipmentTypes();
            log.info("Retrieved {} available categories", categories.size());
            return categories;
        } catch (Exception e) {
            log.error("Failed to get available categories: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
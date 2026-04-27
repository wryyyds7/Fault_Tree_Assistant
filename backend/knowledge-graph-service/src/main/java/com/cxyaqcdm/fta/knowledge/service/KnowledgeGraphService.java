package com.cxyaqcdm.fta.knowledge.service;

import java.util.Map;

public interface KnowledgeGraphService {
    Map<String, Object> queryTemplate(String topEvent, String equipmentType);

    Map<String, Object> getKnowledgeGraphData(String userId);

    void enrichKnowledge(Map<String, Object> causalPattern);

    void deleteUserDocumentKnowledge(String userId, String docId);

    void initializeOntology();

    void saveEvent(Map<String, Object> eventData);
}

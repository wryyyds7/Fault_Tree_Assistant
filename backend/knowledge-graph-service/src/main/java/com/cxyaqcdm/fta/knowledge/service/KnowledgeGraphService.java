package com.cxyaqcdm.fta.knowledge.service;

import java.util.Map;

public interface KnowledgeGraphService {
    Map<String, Object> queryTemplate(String topEvent, String equipmentType);
    void enrichKnowledge(Map<String, Object> causalPattern);
    void initializeOntology();
}

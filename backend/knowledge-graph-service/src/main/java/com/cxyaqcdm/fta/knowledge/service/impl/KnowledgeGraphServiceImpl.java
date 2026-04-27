package com.cxyaqcdm.fta.knowledge.service.impl;

import com.cxyaqcdm.fta.knowledge.dto.RelationshipQueryResult;
import com.cxyaqcdm.fta.knowledge.entity.UserEvent;
import com.cxyaqcdm.fta.knowledge.entity.GlobalEvent;
import com.cxyaqcdm.fta.knowledge.repository.KnowledgeGraphRepository;
import com.cxyaqcdm.fta.knowledge.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final Neo4jClient neo4jClient;

    @Value("${knowledge.graph.ontology.path}")
    private String ontologyPath;

    @Value("${knowledge.graph.templates.path}")
    private String templatesPath;

    @Override
    public Map<String, Object> queryTemplate(String topEvent, String equipmentType) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Querying template for topEvent: {}, equipmentType: {}", topEvent, equipmentType);

            List<TemplateMatchResult> matches = new ArrayList<>();

            List<UserEvent> exactMatches = knowledgeGraphRepository.findUserAndGlobalEvents(null);
            exactMatches = exactMatches.stream()
                    .filter(e -> e.getName() != null && e.getName().equals(topEvent))
                    .filter(e -> e.getEquipmentType() == null || e.getEquipmentType().equals(equipmentType))
                    .collect(Collectors.toList());

            if (!exactMatches.isEmpty()) {
                for (UserEvent event : exactMatches) {
                    matches.add(new TemplateMatchResult(event, 1.0, "EXACT_MATCH"));
                }
            }

            if (matches.isEmpty()) {
                List<TemplateMatchResult> fuzzyMatches = findFuzzyMatches(topEvent, equipmentType);
                matches.addAll(fuzzyMatches);
            }

            matches.sort((a, b) -> Double.compare(b.score, a.score));

            if (!matches.isEmpty()) {
                TemplateMatchResult bestMatch = matches.get(0);
                log.info("Best template match found: {}, score: {}, matchType: {}",
                        bestMatch.event.getName(), bestMatch.score, bestMatch.matchType);

                Map<String, Object> structure = buildTemplateStructure(bestMatch.event.getId(), topEvent);
                result.put("templateId", "tmpl_" + bestMatch.event.getId());
                result.put("structure", structure);
                result.put("matchScore", bestMatch.score);
                result.put("matchType", bestMatch.matchType);
                result.put("alternativeMatches", matches.stream()
                        .skip(1)
                        .limit(3)
                        .map(m -> {
                            Map<String, Object> alt = new HashMap<>();
                            alt.put("eventName", m.event.getName());
                            alt.put("score", m.score);
                            alt.put("matchType", m.matchType);
                            return alt;
                        })
                        .collect(Collectors.toList()));
            } else {
                log.info("No template matches found, using default template for: {}", topEvent);
                result.put("templateId", "tmpl_default_" + UUID.randomUUID().toString().replace("-", ""));
                result.put("structure", buildDefaultStructure(topEvent, equipmentType));
                result.put("matchScore", 0.0);
                result.put("matchType", "DEFAULT_TEMPLATE");
            }

        } catch (Exception e) {
            log.error("Failed to query template: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> getKnowledgeGraphData(String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Getting knowledge graph data for userId: {}", userId);

            List<Map<String, Object>> nodes = new ArrayList<>();
            Set<String> nodeIds = new HashSet<>();

            // 直接用 Neo4jClient 查询 GlobalEvent 节点
            log.info("Querying GlobalEvent nodes with Neo4jClient");
            List<Map<String, Object>> globalNodeMaps = neo4jClient.query(
                    "MATCH (g:GlobalEvent) RETURN g"
            ).fetch().all().stream()
                    .map(row -> convertToMap(row.get("g")))
                    .filter(map -> map != null)
                    .toList();

            log.info("Found {} GlobalEvent nodes", globalNodeMaps.size());

            for (Map<String, Object> nodeMap : globalNodeMaps) {
                String id = (String) nodeMap.get("id");
                if (id != null) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", id);
                    node.put("name", nodeMap.get("name"));
                    node.put("type", "GLOBAL");
                    node.put("eventType", nodeMap.get("type"));
                    node.put("description", nodeMap.get("description"));
                    node.put("equipmentType", nodeMap.get("equipmentType"));
                    node.put("severity", nodeMap.get("severity"));
                    node.put("probability", nodeMap.get("probability"));
                    nodes.add(node);
                    nodeIds.add(id);
                    log.info("Added GlobalEvent node: id={}, name={}", id, nodeMap.get("name"));
                }
            }

            // 查询 UserEvent 节点
            log.info("Querying UserEvent nodes with Neo4jClient");
            List<Map<String, Object>> userNodeMaps = neo4jClient.query(
                    "MATCH (u:UserEvent) WHERE u.userId = $userId RETURN u"
            ).bind(userId).to("userId").fetch().all().stream()
                    .map(row -> convertToMap(row.get("u")))
                    .filter(map -> map != null)
                    .toList();

            log.info("Found {} UserEvent nodes", userNodeMaps.size());

            for (Map<String, Object> nodeMap : userNodeMaps) {
                String id = (String) nodeMap.get("id");
                if (id != null && !nodeIds.contains(id)) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", id);
                    node.put("name", nodeMap.get("name"));
                    node.put("type", "USER");
                    node.put("eventType", nodeMap.get("type"));
                    node.put("description", nodeMap.get("description"));
                    node.put("equipmentType", nodeMap.get("equipmentType"));
                    node.put("severity", nodeMap.get("severity"));
                    node.put("probability", nodeMap.get("probability"));
                    node.put("userId", nodeMap.get("userId"));
                    node.put("docId", nodeMap.get("docId"));
                    nodes.add(node);
                    nodeIds.add(id);
                    log.info("Added UserEvent node: id={}, name={}", id, nodeMap.get("name"));
                }
            }

            List<Map<String, Object>> relationships = new ArrayList<>();

            // 直接用 Neo4jClient 查询用户事件关系
            log.info("Querying user event relationships for userId: {}", userId);
            List<Map<String, Object>> userRels = queryRelationships(
                    "MATCH (source:UserEvent)-[rel:CAUSES]->(target:UserEvent) " +
                    "WHERE source.userId = $userId RETURN source, rel, target",
                    userId
            );

            // 查询全局事件关系
            log.info("Querying global event relationships");
            List<Map<String, Object>> globalRels = queryRelationships(
                    "MATCH (source:GlobalEvent)-[rel:CAUSES]->(target:GlobalEvent) " +
                    "RETURN source, rel, target",
                    null
            );

            log.info("Found {} user relationships and {} global relationships",
                    userRels.size(), globalRels.size());
            
            // 处理用户关系
            for (Map<String, Object> row : userRels) {
                Map<String, Object> sourceMap = (Map<String, Object>) row.get("source");
                Map<String, Object> relMap = (Map<String, Object>) row.get("rel");
                Map<String, Object> targetMap = (Map<String, Object>) row.get("target");
                
                if (sourceMap != null && targetMap != null) {
                    String sourceId = (String) sourceMap.get("id");
                    String targetId = (String) targetMap.get("id");
                    
                    log.info("User relation: {} -> {}", sourceId, targetId);
                    
                    if (sourceId != null && targetId != null && 
                        nodeIds.contains(sourceId) && nodeIds.contains(targetId)) {
                        Map<String, Object> relationship = new HashMap<>();
                        relationship.put("source", sourceId);
                        relationship.put("target", targetId);
                        relationship.put("description", relMap != null ? relMap.get("description") : null);
                        relationship.put("gateType", relMap != null ? relMap.get("gateType") : null);
                        relationship.put("confidence", relMap != null ? relMap.get("confidence") : null);
                        relationship.put("type", "USER");
                        relationships.add(relationship);
                        log.info("Added user relation: {} -> {}", sourceId, targetId);
                    }
                }
            }
            
            // 处理全局关系 - 简化处理，直接用节点的 id 字段
            for (Map<String, Object> row : globalRels) {
                Map<String, Object> sourceMap = (Map<String, Object>) row.get("source");
                Map<String, Object> relMap = (Map<String, Object>) row.get("rel");
                Map<String, Object> targetMap = (Map<String, Object>) row.get("target");
                
                if (sourceMap != null && targetMap != null) {
                    String sourceId = (String) sourceMap.get("id");
                    String targetId = (String) targetMap.get("id");
                    
                    log.info("Global relation: {} -> {}", sourceId, targetId);
                    
                    if (sourceId != null && targetId != null) {
                        Map<String, Object> relationship = new HashMap<>();
                        relationship.put("source", sourceId);
                        relationship.put("target", targetId);
                        relationship.put("description", relMap != null ? relMap.get("description") : null);
                        relationship.put("gateType", relMap != null ? relMap.get("gateType") : null);
                        relationship.put("confidence", relMap != null ? relMap.get("confidence") : null);
                        relationship.put("type", "GLOBAL");
                        relationships.add(relationship);
                        log.info("Added global relation: {} -> {}", sourceId, targetId);
                    }
                }
            }

            result.put("nodes", nodes);
            result.put("relationships", relationships);
            result.put("userEventCount", userNodeMaps.size());
            result.put("globalEventCount", globalNodeMaps.size());
            log.info("Knowledge graph data: {} nodes, {} relationships",
                    nodes.size(), relationships.size());
            
            // 打印详细的返回数据
            log.info("===== RETURN DATA =====");
            log.info("Nodes: {}", nodes);
            log.info("Relationships: {}", relationships);
            log.info("Relationships count: {}", relationships.size());
            for (int i = 0; i < Math.min(3, relationships.size()); i++) {
                log.info("Relationship {}: {}", i, relationships.get(i));
            }
            log.info("======================");

        } catch (Exception e) {
            log.error("Failed to get knowledge graph data: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Override
    public void enrichKnowledge(Map<String, Object> causalPattern) {
        try {
            log.info("========================================");
            log.info("[DEBUG] enrichKnowledge called!");
            log.info("[DEBUG] Input causalPattern: {}", causalPattern);
            log.info("========================================");
            
            String userId = (String) causalPattern.getOrDefault("userId", "anonymous");
            String docId = (String) causalPattern.get("docId");
            String cause = (String) causalPattern.get("cause");
            String effect = (String) causalPattern.get("effect");
            String gateType = (String) causalPattern.getOrDefault("gateType", "OR");
            Double confidence = causalPattern.get("confidence") != null
                    ? ((Number) causalPattern.get("confidence")).doubleValue()
                    : 0.8;
            String equipmentType = (String) causalPattern.get("equipmentType");
            String causeEventType = (String) causalPattern.getOrDefault("causeEventType", "底事件");
            String effectEventType = (String) causalPattern.getOrDefault("effectEventType", "中间事件");
            String causeDescription = (String) causalPattern.get("causeDescription");
            String effectDescription = (String) causalPattern.get("effectDescription");

            if (cause == null || effect == null) {
                log.warn("Invalid causal pattern: missing cause or effect");
                return;
            }

            String causeId = "ue_" + userId + "_" + Math.abs(cause.hashCode());
            String effectId = "ue_" + userId + "_" + Math.abs(effect.hashCode());

            // 检查是否已存在该节点，避免重复创建
            UserEvent existingCause = knowledgeGraphRepository.findUserEventById(causeId);
            UserEvent existingEffect = knowledgeGraphRepository.findUserEventById(effectId);

            UserEvent causeEvent;
            if (existingCause != null) {
                causeEvent = existingCause;
                log.info("Reusing existing cause event: {}", cause);
            } else {
                causeEvent = UserEvent.builder()
                        .id(causeId)
                        .name(cause)
                        .type(causeEventType)
                        .equipmentType(equipmentType)
                        .userId(userId)
                        .docId(docId)
                        .description(causeDescription)
                        .severity("MEDIUM")
                        .probability(0.1)
                        .isGlobal(false)
                        .build();
                log.info("[DEBUG] Saving CAUSE event: id={}, name={}, type={}, desc={}", 
                        causeId, cause, causeEventType, causeDescription);
                knowledgeGraphRepository.save(causeEvent);
            }

            UserEvent effectEvent;
            if (existingEffect != null) {
                effectEvent = existingEffect;
                log.info("Reusing existing effect event: {}", effect);
            } else {
                effectEvent = UserEvent.builder()
                        .id(effectId)
                        .name(effect)
                        .type(effectEventType)
                        .equipmentType(equipmentType)
                        .userId(userId)
                        .docId(docId)
                        .description(effectDescription)
                        .severity("MEDIUM")
                        .probability(0.1)
                        .isGlobal(false)
                        .build();
                log.info("[DEBUG] Saving EFFECT event: id={}, name={}, type={}, desc={}", 
                        effectId, effect, effectEventType, effectDescription);
                knowledgeGraphRepository.save(effectEvent);
            }

            // 检查关系是否已存在，避免重复创建
            boolean relationExists = knowledgeGraphRepository.existsRelation(causeId, effectId);
            if (!relationExists) {
                String cypher = "MATCH (e1:UserEvent {id: $causeId}), (e2:UserEvent {id: $effectId}) " +
                        "CREATE (e1)-[r:CAUSES {gateType: $gateType, description: $description, " +
                        "confidence: $confidence, isGlobal: false}]->(e2)";
                Map<String, Object> params = new HashMap<>();
                params.put("causeId", causeId);
                params.put("effectId", effectId);
                params.put("gateType", gateType);
                params.put("description", cause + " 导致 " + effect);
                params.put("confidence", confidence);

                neo4jClient.query(cypher).bindAll(params).run();
                log.info("Created new relation: {} -> {} for user {}", cause, effect, userId);
            } else {
                log.info("Relation already exists: {} -> {}", cause, effect);
            }

        } catch (Exception e) {
            log.error("Failed to enrich knowledge: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteUserDocumentKnowledge(String userId, String docId) {
        try {
            log.info("Deleting knowledge graph data for userId={}, docId={}", userId, docId);
            knowledgeGraphRepository.deleteEventsByUserIdAndDocId(userId, docId);
            log.info("Successfully deleted knowledge graph data for userId={}, docId={}", userId, docId);
        } catch (Exception e) {
            log.error("Failed to delete user document knowledge: {}", e.getMessage(), e);
        }
    }

    @Override
    public void initializeOntology() {
        log.info("Initializing ontology from path: {}", ontologyPath);
        log.info("Initializing templates from path: {}", templatesPath);
        log.info("Ontology initialization completed. Make sure Neo4j is initialized with neo4j_init.cypher");
    }

    @Override
    public void saveEvent(Map<String, Object> eventData) {
        try {
            log.info("========================================");
            log.info("[DEBUG] saveEvent called!");
            log.info("[DEBUG] Input eventData: {}", eventData);
            log.info("========================================");
            
            String userId = (String) eventData.getOrDefault("userId", "anonymous");
            String docId = (String) eventData.get("docId");
            String name = (String) eventData.get("name");
            String eventType = (String) eventData.getOrDefault("eventType", "底事件");
            String equipmentType = (String) eventData.get("equipmentType");
            String description = (String) eventData.get("description");
            String severity = (String) eventData.getOrDefault("severity", null);
            Double probability = eventData.get("probability") != null
                    ? ((Number) eventData.get("probability")).doubleValue()
                    : null;

            if (name == null || name.trim().isEmpty()) {
                log.warn("Invalid event data: missing name");
                return;
            }

            String eventId = "ue_" + userId + "_" + Math.abs(name.hashCode());

            // 检查是否已存在该节点，避免重复创建
            UserEvent existingEvent = knowledgeGraphRepository.findUserEventById(eventId);

            if (existingEvent != null) {
                log.info("Event already exists, updating: {}", name);
                existingEvent.setType(eventType);
                if (equipmentType != null) existingEvent.setEquipmentType(equipmentType);
                if (description != null) existingEvent.setDescription(description);
                if (severity != null) existingEvent.setSeverity(severity);
                if (probability != null) existingEvent.setProbability(probability);
                knowledgeGraphRepository.save(existingEvent);
            } else {
                UserEvent event = UserEvent.builder()
                        .id(eventId)
                        .name(name)
                        .type(eventType)
                        .equipmentType(equipmentType)
                        .userId(userId)
                        .docId(docId)
                        .description(description != null ? description : "")
                        .severity(severity != null ? severity : "MEDIUM")
                        .probability(probability != null ? probability : 0.1)
                        .isGlobal(false)
                        .build();
                log.info("[DEBUG] Saving event: id={}, name={}, type={}, desc={}", 
                        eventId, name, eventType, description);
                knowledgeGraphRepository.save(event);
                log.info("Saved new event: {} for user {}", name, userId);
            }

        } catch (Exception e) {
            log.error("Failed to save event: {}", e.getMessage(), e);
        }
    }

    private List<TemplateMatchResult> findFuzzyMatches(String topEvent, String equipmentType) {
        List<TemplateMatchResult> results = new ArrayList<>();
        List<UserEvent> allUserEvents = knowledgeGraphRepository.findUserAndGlobalEvents(null);

        for (UserEvent event : allUserEvents) {
            double nameSim = calculateSimilarity(topEvent, event.getName());
            double equipSim = (equipmentType != null && event.getEquipmentType() != null)
                    ? calculateSimilarity(equipmentType, event.getEquipmentType())
                    : 0.5;

            double totalScore = nameSim * 0.7 + equipSim * 0.3;

            if (totalScore > 0.5) {
                results.add(new TemplateMatchResult(event, totalScore, "FUZZY_MATCH"));
            }
        }

        return results;
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        s1 = s1.toLowerCase().trim();
        s2 = s2.toLowerCase().trim();

        if (s1.equals(s2)) {
            return 1.0;
        }

        if (s1.contains(s2) || s2.contains(s1)) {
            return 0.8;
        }

        int maxLength = Math.max(s1.length(), s2.length());
        int distance = levenshteinDistance(s1, s2);

        return 1.0 - (double) distance / maxLength;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private Map<String, Object> buildTemplateStructure(String eventId, String targetEvent) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("event", targetEvent);
        structure.put("gate", "OR");
        structure.put("children", new ArrayList<>());

        try {
            String cypher = "MATCH (e:UserEvent {id: $eventId})-[r:CAUSES]->(child:UserEvent) " +
                    "RETURN child, r.gateType as gateType " +
                    "ORDER BY r.confidence DESC";

            Map<String, Object> params = new HashMap<>();
            params.put("eventId", eventId);

            List<Map<String, Object>> queryResults = neo4jClient.query(cypher)
                    .bindAll(params)
                    .fetch()
                    .all()
                    .stream()
                    .collect(Collectors.toList());

            for (Map<String, Object> row : queryResults) {
                Object childObj = row.get("child");
                if (childObj instanceof UserEvent) {
                    UserEvent childEvent = (UserEvent) childObj;
                    Map<String, Object> child = new HashMap<>();
                    child.put("event", childEvent.getName());
                    child.put("gate", row.get("gateType") != null ? row.get("gateType") : "OR");
                    child.put("children", new ArrayList<>());
                    ((List<Object>) structure.get("children")).add(child);
                }
            }

            if (((List<?>) structure.get("children")).isEmpty()) {
                structure.put("children", getDefaultChildren(null));
            }

        } catch (Exception e) {
            log.warn("Failed to build template structure: {}", e.getMessage());
            structure.put("children", getDefaultChildren(null));
        }

        return structure;
    }

    private Map<String, Object> buildDefaultStructure(String topEvent, String equipmentType) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("event", topEvent);
        structure.put("gate", "OR");
        structure.put("children", getDefaultChildren(equipmentType));
        return structure;
    }

    private List<Map<String, Object>> getDefaultChildren(String equipmentType) {
        List<Map<String, Object>> children = new ArrayList<>();

        Map<String, Object> child1 = new HashMap<>();
        child1.put("event", "设备老化");
        child1.put("gate", "OR");
        child1.put("children", new ArrayList<>());
        children.add(child1);

        Map<String, Object> child2 = new HashMap<>();
        child2.put("event", "操作不当");
        child2.put("gate", "OR");
        child2.put("children", new ArrayList<>());
        children.add(child2);

        Map<String, Object> child3 = new HashMap<>();
        child3.put("event", "环境因素");
        child3.put("gate", "OR");
        child3.put("children", new ArrayList<>());
        children.add(child3);

        return children;
    }

    private List<Map<String, Object>> queryRelationships(String cypher, String userId) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            log.info("Executing query: {}", cypher);
            
            List<Map<String, Object>> queryResults;
            if (userId != null) {
                queryResults = new ArrayList<>(
                    neo4jClient.query(cypher)
                        .bind(userId).to("userId")
                        .fetch()
                        .all()
                );
            } else {
                queryResults = new ArrayList<>(
                    neo4jClient.query(cypher)
                        .fetch()
                        .all()
                );
            }

            log.info("Query returned {} results", queryResults.size());
            
            for (Map<String, Object> row : queryResults) {
                try {
                    log.info("Processing row keys: {}", row.keySet());
                    
                    Object source = row.get("source");
                    Object rel = row.get("rel");
                    Object target = row.get("target");
                    
                    Map<String, Object> sourceMap = convertToMap(source);
                    Map<String, Object> relMap = convertToMap(rel);
                    Map<String, Object> targetMap = convertToMap(target);
                    
                    log.info("  source id: {}", sourceMap != null ? sourceMap.get("id") : "null");
                    log.info("  target id: {}", targetMap != null ? targetMap.get("id") : "null");
                    
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("source", sourceMap);
                    resultMap.put("rel", relMap);
                    resultMap.put("target", targetMap);
                    results.add(resultMap);
                } catch (Exception e) {
                    log.error("Error processing row: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to query relationships: {}", e.getMessage(), e);
        }
        return results;
    }
    
    private Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        try {
            // 处理 Neo4j Node 类型
            Class<?> nodeClass = Class.forName("org.neo4j.driver.types.Node");
            if (nodeClass.isInstance(obj)) {
                Map<String, Object> map = new HashMap<>();
                // 获取所有属性
                Object properties = nodeClass.getMethod("asMap").invoke(obj);
                if (properties instanceof Map) {
                    map.putAll((Map<String, Object>) properties);
                }
                // 获取 elementId
                Object elementId = nodeClass.getMethod("elementId").invoke(obj);
                if (elementId != null) {
                    map.put("elementId", elementId.toString());
                }
                log.info("Converted Node to Map: {}", map);
                return map;
            }
            
            // 处理 Neo4j Relationship 类型
            Class<?> relClass = Class.forName("org.neo4j.driver.types.Relationship");
            if (relClass.isInstance(obj)) {
                Map<String, Object> map = new HashMap<>();
                Object properties = relClass.getMethod("asMap").invoke(obj);
                if (properties instanceof Map) {
                    map.putAll((Map<String, Object>) properties);
                }
                log.info("Converted Relationship to Map: {}", map);
                return map;
            }
        } catch (Exception e) {
            log.warn("Could not convert object: {}", e.getMessage());
        }
        log.warn("Unknown object type: {}", obj.getClass().getName());
        return null;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class TemplateMatchResult {
        private UserEvent event;
        private double score;
        private String matchType;
    }
}

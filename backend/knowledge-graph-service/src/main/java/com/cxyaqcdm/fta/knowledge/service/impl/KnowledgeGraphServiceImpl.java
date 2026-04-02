package com.cxyaqcdm.fta.knowledge.service.impl;

import com.cxyaqcdm.fta.knowledge.entity.Event;
import com.cxyaqcdm.fta.knowledge.repository.EventRepository;
import com.cxyaqcdm.fta.knowledge.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final EventRepository eventRepository;
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
            
            List<Event> exactMatches = eventRepository.findByEventNameAndEquipmentType(topEvent, equipmentType);
            if (!exactMatches.isEmpty()) {
                for (Event event : exactMatches) {
                    matches.add(new TemplateMatchResult(event, 1.0, "EXACT_MATCH"));
                }
            }
            
            if (matches.isEmpty()) {
                List<TemplateMatchResult> fuzzyMatches = findFuzzyMatches(topEvent, equipmentType);
                matches.addAll(fuzzyMatches);
            }
            
            if (matches.isEmpty()) {
                List<Event> equipmentTypeMatches = eventRepository.findByEquipmentType(equipmentType);
                for (Event event : equipmentTypeMatches) {
                    double score = calculateSimilarity(topEvent, event.getName());
                    if (score > 0.3) {
                        matches.add(new TemplateMatchResult(event, score, "EQUIPMENT_TYPE_MATCH"));
                    }
                }
            }
            
            matches.sort((a, b) -> Double.compare(b.score, a.score));
            
            if (!matches.isEmpty()) {
                TemplateMatchResult bestMatch = matches.get(0);
                log.info("Best template match found: {}, score: {}, matchType: {}", 
                    bestMatch.event.getName(), bestMatch.score, bestMatch.matchType);
                
                Map<String, Object> structure = buildTemplateStructure(bestMatch.event, topEvent);
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
            result.put("templateId", "tmpl_default_error");
            result.put("structure", buildDefaultStructure(topEvent, equipmentType));
            result.put("matchScore", 0.0);
            result.put("matchType", "ERROR_FALLBACK");
        }
        return result;
    }
    
    private List<TemplateMatchResult> findFuzzyMatches(String topEvent, String equipmentType) {
        List<TemplateMatchResult> results = new ArrayList<>();
        List<Event> allEvents = eventRepository.findAll();
        
        for (Event event : allEvents) {
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
    
    private Map<String, Object> buildTemplateStructure(Event event, String targetEvent) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("event", targetEvent);
        structure.put("gate", "OR");
        structure.put("children", new ArrayList<>());
        
        try {
            String cypher = "MATCH (e:Event {id: $eventId})-[:CAUSES*1..3]->(child:Event) " +
                           "RETURN child, r.gateType " +
                           "ORDER BY length(r)";
            
            Map<String, Object> params = new HashMap<>();
            params.put("eventId", event.getId());
            
            List<Map<String, Object>> queryResults = new ArrayList<>(neo4jClient.query(cypher)
                .bindAll(params)
                .fetch()
                .all());
            
            for (Map<String, Object> row : queryResults) {
                Map<String, Object> child = new HashMap<>();
                child.put("event", ((Event) row.get("child")).getName());
                child.put("gate", row.get("gateType") != null ? row.get("gateType") : "OR");
                child.put("children", new ArrayList<>());
                ((List<Object>) structure.get("children")).add(child);
            }
            
            if (((List<?>) structure.get("children")).isEmpty()) {
                structure.put("children", getDefaultChildren(event.getEquipmentType()));
            }
            
        } catch (Exception e) {
            log.warn("Failed to build template structure from graph, using defaults: {}", e.getMessage());
            structure.put("children", getDefaultChildren(event.getEquipmentType()));
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
        
        Map<String, Object> cat1 = new HashMap<>();
        cat1.put("event", "电源/电气问题");
        cat1.put("gate", "OR");
        cat1.put("children", new ArrayList<>());
        
        Map<String, Object> cat2 = new HashMap<>();
        cat2.put("event", "机械/结构问题");
        cat2.put("gate", "OR");
        cat2.put("children", new ArrayList<>());
        
        Map<String, Object> cat3 = new HashMap<>();
        cat3.put("event", "环境/操作问题");
        cat3.put("gate", "OR");
        cat3.put("children", new ArrayList<>());
        
        children.add(cat1);
        children.add(cat2);
        children.add(cat3);
        
        return children;
    }
    
    private static class TemplateMatchResult {
        Event event;
        double score;
        String matchType;
        
        TemplateMatchResult(Event event, double score, String matchType) {
            this.event = event;
            this.score = score;
            this.matchType = matchType;
        }
    }

    @Override
    public void enrichKnowledge(Map<String, Object> causalPattern) {
        try {
            // 提取因果模式
            String cause = (String) causalPattern.get("cause");
            String effect = (String) causalPattern.get("effect");
            String equipmentType = (String) causalPattern.get("equipmentType");
            String gateType = (String) causalPattern.get("gateType");
            
            // 创建或更新事件
            Event causeEvent = createOrUpdateEvent(cause, "INTERMEDIATE", equipmentType);
            Event effectEvent = createOrUpdateEvent(effect, "INTERMEDIATE", equipmentType);
            
            // 创建关系
            String cypher = "MATCH (c:Event {id: $causeId}), (e:Event {id: $effectId}) " +
                           "MERGE (c)-[r:CAUSES]->(e) " +
                           "SET r.gateType = $gateType, r.equipmentType = $equipmentType, r.timestamp = timestamp()";
            
            Map<String, Object> params = new HashMap<>();
            params.put("causeId", causeEvent.getId());
            params.put("effectId", effectEvent.getId());
            params.put("gateType", gateType != null ? gateType : "OR");
            params.put("equipmentType", equipmentType);
            
            neo4jClient.query(cypher)
                    .bindAll(params)
                    .run();
            
            log.info("Knowledge enriched: {} -> {}, relationship created with gate type: {}", 
                     cause, effect, gateType != null ? gateType : "OR");
        } catch (Exception e) {
            log.error("Failed to enrich knowledge: {}", e.getMessage());
        }
    }

    @Override
    public void initializeOntology() {
        try {
            // 加载ISO 13379本体
            loadISO13379Ontology();
            
            // 预加载常见故障模板
            preloadCommonTemplates();
            
            log.info("Ontology initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize ontology: {}", e.getMessage());
        }
    }
    
    private void loadISO13379Ontology() {
        try {
            // 这里实现ISO 13379本体的加载逻辑
            // 例如：创建基本的事件类型、关系类型等
            
            // 创建本体根节点
            String cypher = "MERGE (o:Ontology {name: 'ISO 13379', version: '1.0'})";
            neo4jClient.query(cypher).run();
            
            // 创建事件类型
            String[] eventTypes = {"TOP", "INTERMEDIATE", "BASIC"};
            for (String type : eventTypes) {
                cypher = "MERGE (t:EventType {name: $type}) MERGE (o:Ontology {name: 'ISO 13379'}) MERGE (o)-[:HAS_TYPE]->(t)";
                neo4jClient.query(cypher)
                        .bind(type).to("type")
                        .run();
            }
            
            // 创建关系类型
            String[] relationshipTypes = {"CAUSES", "PART_OF"};
            for (String type : relationshipTypes) {
                cypher = "MERGE (r:RelationshipType {name: $type}) MERGE (o:Ontology {name: 'ISO 13379'}) MERGE (o)-[:HAS_RELATIONSHIP_TYPE]->(r)";
                neo4jClient.query(cypher)
                        .bind(type).to("type")
                        .run();
            }
            
            log.info("ISO 13379 ontology loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load ISO 13379 ontology: {}", e.getMessage());
        }
    }

    private Event createOrUpdateEvent(String name, String type, String equipmentType) {
        // 生成事件ID
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        
        // 创建事件
        Event event = new Event();
        event.setId(eventId);
        event.setName(name);
        event.setType(type);
        event.setEquipmentType(equipmentType);
        event.setDescription("Auto-generated event");
        
        // 保存事件
        return eventRepository.save(event);
    }

    private void preloadCommonTemplates() {
        // 预加载常见设备类型的故障模板
        // 例如：电机、液压泵、传感器等
        String[] equipmentTypes = {"induction_motor", "hydraulic_pump", "sensor"};
        
        for (String equipmentType : equipmentTypes) {
            // 为每种设备类型创建默认事件
            createDefaultEvents(equipmentType);
            // 创建事件之间的关系
            createDefaultRelationships(equipmentType);
        }
    }

    private void createDefaultEvents(String equipmentType) {
        // 根据设备类型创建默认事件
        switch (equipmentType) {
            case "induction_motor":
                createEvent("电机过热", "TOP", equipmentType);
                createEvent("电源问题", "INTERMEDIATE", equipmentType);
                createEvent("轴承故障", "INTERMEDIATE", equipmentType);
                createEvent("绕组故障", "INTERMEDIATE", equipmentType);
                break;
            case "hydraulic_pump":
                createEvent("压力不足", "TOP", equipmentType);
                createEvent("泵磨损", "INTERMEDIATE", equipmentType);
                createEvent("油液污染", "INTERMEDIATE", equipmentType);
                createEvent("密封失效", "INTERMEDIATE", equipmentType);
                break;
            case "sensor":
                createEvent("信号异常", "TOP", equipmentType);
                createEvent("电源故障", "INTERMEDIATE", equipmentType);
                createEvent("传感器损坏", "INTERMEDIATE", equipmentType);
                createEvent("连接松动", "INTERMEDIATE", equipmentType);
                break;
            default:
                break;
        }
    }
    
    private void createDefaultRelationships(String equipmentType) {
        try {
            switch (equipmentType) {
                case "induction_motor":
                    // 创建电机过热的原因关系
                    createRelationship("电源问题", "电机过热", equipmentType, "OR");
                    createRelationship("轴承故障", "电机过热", equipmentType, "OR");
                    createRelationship("绕组故障", "电机过热", equipmentType, "OR");
                    break;
                case "hydraulic_pump":
                    // 创建压力不足的原因关系
                    createRelationship("泵磨损", "压力不足", equipmentType, "OR");
                    createRelationship("油液污染", "压力不足", equipmentType, "OR");
                    createRelationship("密封失效", "压力不足", equipmentType, "OR");
                    break;
                case "sensor":
                    // 创建信号异常的原因关系
                    createRelationship("电源故障", "信号异常", equipmentType, "OR");
                    createRelationship("传感器损坏", "信号异常", equipmentType, "OR");
                    createRelationship("连接松动", "信号异常", equipmentType, "OR");
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to create default relationships for {}: {}", equipmentType, e.getMessage());
        }
    }
    
    private void createRelationship(String causeName, String effectName, String equipmentType, String gateType) {
        // 查找事件
        String cypher = "MATCH (c:Event {name: $causeName, equipmentType: $equipmentType}) " +
                       "MATCH (e:Event {name: $effectName, equipmentType: $equipmentType}) " +
                       "MERGE (c)-[r:CAUSES]->(e) " +
                       "SET r.gateType = $gateType, r.equipmentType = $equipmentType, r.timestamp = timestamp()";
        
        neo4jClient.query(cypher)
                .bind(causeName).to("causeName")
                .bind(effectName).to("effectName")
                .bind(equipmentType).to("equipmentType")
                .bind(gateType).to("gateType")
                .run();
    }

    private void createEvent(String name, String type, String equipmentType) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        Event event = new Event();
        event.setId(eventId);
        event.setName(name);
        event.setType(type);
        event.setEquipmentType(equipmentType);
        event.setDescription("Default event for " + equipmentType);
        eventRepository.save(event);
    }
}

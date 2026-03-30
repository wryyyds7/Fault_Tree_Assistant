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
            // 查询匹配的事件
            List<Event> events = eventRepository.findByEventNameAndEquipmentType(topEvent, equipmentType);
            
            if (!events.isEmpty()) {
                // 构建模板结构
                Map<String, Object> structure = new HashMap<>();
                structure.put("event", topEvent);
                structure.put("gate", "OR"); // 默认使用OR门
                structure.put("children", new ArrayList<>());
                
                result.put("templateId", "tmpl_" + UUID.randomUUID().toString().replace("-", ""));
                result.put("structure", structure);
            } else {
                // 返回默认模板
                Map<String, Object> structure = new HashMap<>();
                structure.put("event", topEvent);
                structure.put("gate", "OR");
                structure.put("children", new ArrayList<>());
                
                result.put("templateId", "tmpl_default_" + equipmentType);
                result.put("structure", structure);
            }
        } catch (Exception e) {
            log.error("Failed to query template: {}", e.getMessage());
            // 返回默认模板
            Map<String, Object> structure = new HashMap<>();
            structure.put("event", topEvent);
            structure.put("gate", "OR");
            structure.put("children", new ArrayList<>());
            
            result.put("templateId", "tmpl_default");
            result.put("structure", structure);
        }
        return result;
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

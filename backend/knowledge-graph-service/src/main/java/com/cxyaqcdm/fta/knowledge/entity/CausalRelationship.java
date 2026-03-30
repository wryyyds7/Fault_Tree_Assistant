package com.cxyaqcdm.fta.knowledge.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Data
public class CausalRelationship {
    @Id
    @GeneratedValue
    private String id;
    
    private String gateType;
    private String description;
    private double confidence;
    
    @TargetNode
    private Event target;
}

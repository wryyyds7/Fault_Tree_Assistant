package com.cxyaqcdm.fta.knowledge.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node("Event")
@Data
public class Event {
    @Id
    private String id;
    
    @Property("name")
    private String name;
    
    @Property("type")
    private String type;
    
    @Property("description")
    private String description;
    
    @Property("equipmentType")
    private String equipmentType;
}

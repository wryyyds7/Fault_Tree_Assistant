package com.cxyaqcdm.fta.knowledge.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node("UserEvent")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {
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

    @Property("userId")
    private String userId;

    @Property("docId")
    private String docId;

    @Property("severity")
    private String severity;

    @Property("probability")
    private Double probability;

    @Property("isGlobal")
    private Boolean isGlobal;

    @Property("createdAt")
    private String createdAt;
}

package com.cxyaqcdm.fta.knowledge.dto;

import lombok.Data;
import java.util.Map;

@Data
public class RelationshipQueryResult {
    private Map<String, Object> source;
    private Map<String, Object> rel;
    private Map<String, Object> target;
}

package com.cxyaqcdm.fta.document.service.semantic;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SemanticParagraph {
    private String paragraphId;
    private String content;
    private int pageNumber;
    private String sectionTitle;
    private int paragraphIndex;
    private double confidenceScore;
    private List<DomainEntity> entities;
    private List<CausalTriple> causalRelations;
    private String sourceType;
    private double credibilityWeight;

    @Data
    @Builder
    public static class DomainEntity {
        private String text;
        private EntityType type;
        private int startPos;
        private int endPos;
        private double confidence;
    }

    @Data
    @Builder
    public static class CausalTriple {
        private String cause;
        private String effect;
        private String relationType;
        private String signalPhrase;
        private double confidence;
    }

    public enum EntityType {
        FAULT_PHENOMENON,
        COMPONENT,
        SYSTEM,
        FAULT_MODE,
        FAULT_CAUSE,
        CONDITION,
        UNKNOWN
    }
}
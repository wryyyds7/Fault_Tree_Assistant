package com.cxyaqcdm.fta.vector.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ParagraphMetadata {
    private Long id;
    private String paragraphId;
    private String docId;
    private String sectionTitle;
    private Integer pageNumber;
    private Integer paragraphNumber;
    private Integer textLength;
    private String keywords;
    private Double confidenceScore;
    private String content;
    private String sourceType;
    private Double credibilityWeight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String userId;
    
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public Double getCredibilityWeight() {
        if (this.credibilityWeight != null) {
            return this.credibilityWeight;
        }
        if (this.sourceType == null) {
            return 0.5;
        }
        return switch (this.sourceType) {
            case "industry_standard" -> 1.2;
            case "equipment_manual" -> 1.0;
            case "theory_paper" -> 0.9;
            case "maintenance_record" -> 0.8;
            case "user_feedback" -> 0.6;
            default -> 0.5;
        };
    }
}
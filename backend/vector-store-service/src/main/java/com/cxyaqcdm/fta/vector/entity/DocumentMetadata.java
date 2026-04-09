package com.cxyaqcdm.fta.vector.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentMetadata {
    private Long id;
    private String docId;
    private String fileName;
    private String fileType;
    private Integer pageCount;
    private LocalDateTime uploadTime;
    private String equipmentType;
    private String status;
    private String sourceType;
    private Double credibilityWeight;
    private Boolean persistToKnowledgeBase;
    private Boolean isTemporary;
    private Boolean isShared;
    private LocalDateTime expiresAt;
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
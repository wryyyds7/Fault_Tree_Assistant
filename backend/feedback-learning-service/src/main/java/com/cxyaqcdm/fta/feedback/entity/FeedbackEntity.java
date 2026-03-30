package com.cxyaqcdm.fta.feedback.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeedbackEntity {
    private Long id;
    private String feedbackId;
    private String treeId;
    private String userId;
    private Integer rating; // 1-5星
    private String comments;
    private String suggestedChanges;
    private Double accuracyScore;
    private Double completenessScore;
    private Double clarityScore;
    private LocalDateTime createdAt;
    
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}

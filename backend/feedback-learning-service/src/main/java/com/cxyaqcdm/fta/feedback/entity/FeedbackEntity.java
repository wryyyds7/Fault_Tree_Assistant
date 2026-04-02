package com.cxyaqcdm.fta.feedback.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeedbackEntity {
    private Long id;
    private String feedbackId;
    private String treeId;
    private String userId;
    private String feedbackType;
    private Integer rating;
    private String content;
    private String comments;
    private String suggestions;
    private String suggestedChanges;
    private Double accuracyScore;
    private Double completenessScore;
    private Double clarityScore;
    private String status;
    private String processedBy;
    private LocalDateTime processedAt;
    private Integer appliedToModel;
    private LocalDateTime createdAt;

    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}

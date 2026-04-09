package com.cxyaqcdm.fta.vector.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VectorStore {
    private Long id;
    private String vectorId;
    private String paragraphId;
    private String docId;
    private String vectorData;
    private Integer vectorDimension;
    private String embeddingModel;
    private Double similarityScore;
    private String createdBy;
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
}
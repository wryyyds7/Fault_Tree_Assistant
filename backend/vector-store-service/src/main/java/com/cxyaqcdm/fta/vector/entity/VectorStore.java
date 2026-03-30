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
    private Double similarityScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
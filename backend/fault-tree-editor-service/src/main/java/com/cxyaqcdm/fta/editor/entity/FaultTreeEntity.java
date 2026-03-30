package com.cxyaqcdm.fta.editor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FaultTreeEntity {
    private Long id;
    private String treeId;
    private String name;
    private String description;
    private String equipmentType;
    private String treeData;
    private String createdBy;
    private String updatedBy;
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

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
    private String topEvent;
    private String treeData;
    private Integer version;
    private String validationStatus;
    private String validationMessage;
    private String sourceDocIds;
    private String sourceDetail;
    private String publishStatus;
    private String fusionStatistics;
    private String generatedBy;
    private String templateId;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }

    public void setUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}

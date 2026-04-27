package com.cxyaqcdm.fta.editor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FaultTreeVersionEntity {
    private Long id;
    private String versionId;
    private String treeId;
    private String userId;
    private Integer versionNumber;
    private String treeDataSnapshot;
    private String changeSummary;
    private String changedBy;
    private LocalDateTime createdAt;

    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}

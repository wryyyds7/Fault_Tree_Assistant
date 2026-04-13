package com.cxyaqcdm.fta.common.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLog {

    private Long id;
    private String userId;
    private String username;
    private String serviceName;
    private String logLevel;
    private String operationType;
    private String operationDetail;
    private String ipAddress;
    private String requestMethod;
    private String requestPath;
    private String requestParams;
    private Integer responseStatus;
    private Long executionTime;
    private LocalDateTime createTime;

    public OperationLog() {
        this.createTime = LocalDateTime.now();
        this.logLevel = "INFO";
    }
}

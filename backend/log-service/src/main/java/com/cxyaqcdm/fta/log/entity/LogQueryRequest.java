package com.cxyaqcdm.fta.log.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LogQueryRequest {
    private String userId;
    private String serviceName;
    private String logLevel;
    private String operationType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}

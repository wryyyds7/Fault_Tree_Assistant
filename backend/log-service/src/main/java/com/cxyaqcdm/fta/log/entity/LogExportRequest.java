package com.cxyaqcdm.fta.log.entity;

import lombok.Data;
import java.util.List;

@Data
public class LogExportRequest {
    private String userId;
    private String serviceName;
    private String logLevel;
    private String operationType;
    private String startTime;
    private String endTime;
    private String format;
}

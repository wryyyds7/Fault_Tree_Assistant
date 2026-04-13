package com.cxyaqcdm.fta.log.service;

import com.cxyaqcdm.fta.log.entity.OperationLog;
import java.util.List;

public interface LogQueryService {

    List<OperationLog> queryLogs(String userId, String serviceName, String logLevel,
                                  String operationType, String startTime, String endTime,
                                  int pageNum, int pageSize);

    long countLogs(String userId, String serviceName, String logLevel,
                   String operationType, String startTime, String endTime);

    List<OperationLog> getLogsForExport(String userId, String serviceName, String logLevel,
                                         String operationType, String startTime, String endTime);
}

package com.cxyaqcdm.fta.log.service;

import com.cxyaqcdm.fta.log.entity.OperationLog;

public interface LogWriteService {

    void writeLog(OperationLog operationLog);
}

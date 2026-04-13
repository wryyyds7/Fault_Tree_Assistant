package com.cxyaqcdm.fta.log.controller;

import com.cxyaqcdm.fta.log.entity.OperationLog;
import com.cxyaqcdm.fta.log.service.LogExportService;
import com.cxyaqcdm.fta.log.service.LogQueryService;
import com.cxyaqcdm.fta.log.service.LogWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/log")
@RequiredArgsConstructor
@Slf4j
public class LogController {

    private final LogQueryService logQueryService;
    private final LogExportService logExportService;
    private final LogWriteService logWriteService;

    @PostMapping("/write")
    public void writeLog(@RequestBody OperationLog operationLog) {
        logWriteService.writeLog(operationLog);
    }

    @GetMapping("/list")
    public Map<String, Object> queryLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        List<OperationLog> logs = logQueryService.queryLogs(
                userId, serviceName, logLevel, operationType, startTime, endTime, pageNum, pageSize
        );
        long total = logQueryService.countLogs(
                userId, serviceName, logLevel, operationType, startTime, endTime
        );

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", logs);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    @GetMapping("/export/csv")
    public void exportCsv(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) throws Exception {

        List<OperationLog> logs = logQueryService.getLogsForExport(
                userId, serviceName, logLevel, operationType, startTime, endTime
        );
        logExportService.exportToCsv(logs, response);
    }

    @GetMapping("/export/json")
    public void exportJson(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) throws Exception {

        List<OperationLog> logs = logQueryService.getLogsForExport(
                userId, serviceName, logLevel, operationType, startTime, endTime
        );
        logExportService.exportToJson(logs, response);
    }
}

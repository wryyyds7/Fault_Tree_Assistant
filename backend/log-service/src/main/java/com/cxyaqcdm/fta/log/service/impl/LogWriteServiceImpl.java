package com.cxyaqcdm.fta.log.service.impl;

import com.cxyaqcdm.fta.log.entity.OperationLog;
import com.cxyaqcdm.fta.log.repository.OperationLogRepository;
import com.cxyaqcdm.fta.log.service.LogWriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class LogWriteServiceImpl implements LogWriteService {

    private final OperationLogRepository operationLogRepository;

    @Value("${log.file.base-path:../log}")
    private String basePath;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public LogWriteServiceImpl(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Override
    @Async("logTaskExecutor")
    public void writeLog(OperationLog operationLog) {
        operationLogRepository.save(operationLog);

        String serviceDir = operationLog.getServiceName();
        String userId = operationLog.getUserId();
        String userDir = userId != null && !userId.isEmpty() ? sanitizeFileName(userId) : "anonymous";
        String fileName = LocalDate.now().format(DATE_FORMATTER) + ".log";

        Path dirPath = Paths.get(basePath, serviceDir, userDir);
        Path filePath = dirPath.resolve(fileName);

        try {
            Files.createDirectories(dirPath);
            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile(), true))) {
                String logLine = formatLogLine(operationLog);
                writer.println(logLine);
            }
            log.debug("Log written to file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to write log to file: {}", filePath, e);
        }
    }

    private String formatLogLine(OperationLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":").append(log.getId()).append(",");
        sb.append("\"userId\":\"").append(nullSafe(log.getUserId())).append("\",");
        sb.append("\"username\":\"").append(nullSafe(log.getUsername())).append("\",");
        sb.append("\"serviceName\":\"").append(nullSafe(log.getServiceName())).append("\",");
        sb.append("\"logLevel\":\"").append(nullSafe(log.getLogLevel())).append("\",");
        sb.append("\"operationType\":\"").append(nullSafe(log.getOperationType())).append("\",");
        sb.append("\"operationDetail\":\"").append(nullSafe(log.getOperationDetail())).append("\",");
        sb.append("\"ipAddress\":\"").append(nullSafe(log.getIpAddress())).append("\",");
        sb.append("\"requestMethod\":\"").append(nullSafe(log.getRequestMethod())).append("\",");
        sb.append("\"requestPath\":\"").append(nullSafe(log.getRequestPath())).append("\",");
        sb.append("\"requestParams\":\"").append(nullSafe(log.getRequestParams())).append("\",");
        sb.append("\"responseStatus\":").append(log.getResponseStatus()).append(",");
        sb.append("\"executionTime\":").append(log.getExecutionTime()).append(",");
        sb.append("\"createTime\":\"").append(log.getCreateTime() != null ? log.getCreateTime().format(DATE_FORMATTER) : "").append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value.replace("\"", "\\\"") : "";
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}

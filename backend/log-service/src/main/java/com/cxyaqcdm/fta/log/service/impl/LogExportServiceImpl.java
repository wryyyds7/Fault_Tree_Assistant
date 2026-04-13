package com.cxyaqcdm.fta.log.service.impl;

import com.cxyaqcdm.fta.log.entity.OperationLog;
import com.cxyaqcdm.fta.log.service.LogExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class LogExportServiceImpl implements LogExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] CSV_HEADERS = {
            "ID", "用户ID", "用户名", "服务名", "日志级别", "操作类型",
            "操作详情", "IP地址", "请求方法", "请求路径", "请求参数",
            "响应状态", "执行时间(ms)", "创建时间"
    };

    @Override
    public void exportToCsv(List<?> data, HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=operation_logs.csv");

        try (CSVPrinter csvPrinter = new CSVPrinter(response.getWriter(), CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .build())) {

            for (Object item : data) {
                if (item instanceof OperationLog log) {
                    csvPrinter.printRecord(
                            log.getId(),
                            log.getUserId(),
                            log.getUsername(),
                            log.getServiceName(),
                            log.getLogLevel(),
                            log.getOperationType(),
                            log.getOperationDetail(),
                            log.getIpAddress(),
                            log.getRequestMethod(),
                            log.getRequestPath(),
                            log.getRequestParams(),
                            log.getResponseStatus(),
                            log.getExecutionTime(),
                            formatDateTime(log.getCreateTime())
                    );
                }
            }
        } catch (IOException e) {
            log.error("Failed to export CSV", e);
            throw e;
        }
    }

    @Override
    public void exportToJson(List<?> data, HttpServletResponse response) throws Exception {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=operation_logs.json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.writerWithDefaultPrettyPrinter().writeValue(response.getWriter(), data);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}

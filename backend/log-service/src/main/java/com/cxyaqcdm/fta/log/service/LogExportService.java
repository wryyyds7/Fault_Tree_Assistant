package com.cxyaqcdm.fta.log.service;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface LogExportService {

    void exportToCsv(List<?> data, HttpServletResponse response) throws Exception;

    void exportToJson(List<?> data, HttpServletResponse response) throws Exception;
}

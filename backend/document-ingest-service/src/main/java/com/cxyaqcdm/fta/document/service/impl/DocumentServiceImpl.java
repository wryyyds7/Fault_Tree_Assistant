package com.cxyaqcdm.fta.document.service.impl;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.document.client.VectorStoreClient;
import com.cxyaqcdm.fta.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final RabbitTemplate rabbitTemplate;
    private final VectorStoreClient vectorStoreClient;

    @Value("${document.storage.path}")
    private String storagePath;

    /**
     * 获取存储目录的绝对路径
     * 支持环境变量和系统属性覆盖
     */
    private Path getStorageDirectory() {
        String path = storagePath;
        
        // 如果配置了项目根目录系统属性，使用它
        String projectRoot = System.getProperty("project.root");
        if (projectRoot != null && !projectRoot.isEmpty()) {
            path = projectRoot + "/fta-data/documents";
        }
        
        // 解析路径（支持 ~ 表示用户主目录）
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        
        return Paths.get(path).toAbsolutePath().normalize();
    }

    @Override
    public Map<String, Object> uploadDocument(MultipartFile file, String sourceType, String equipmentType, Boolean persistToKnowledgeBase, String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String docId = "doc_" + UUID.randomUUID().toString().replace("-", "");

            Path storageDir = getStorageDirectory();
            log.info("Storage directory: {}", storageDir);

            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
                log.info("Created storage directory: {}", storageDir);
            }

            Path userDir = storageDir.resolve(userId != null ? userId : "anonymous");
            if (!Files.exists(userDir)) {
                Files.createDirectories(userDir);
            }

            String fileName = docId + "_" + file.getOriginalFilename();
            Path filePath = userDir.resolve(fileName);
            file.transferTo(filePath.toFile());

            String actualSourceType = sourceType != null ? sourceType : "manual";
            String actualEquipmentType = (equipmentType != null && !equipmentType.isEmpty()) ? equipmentType : null;
            Boolean actualPersistToKnowledgeBase = persistToKnowledgeBase != null ? persistToKnowledgeBase : true;

            result.put("docId", docId);
            result.put("documentId", docId);
            result.put("status", "解析中");
            result.put("pageCount", 0);
            result.put("sourceType", actualSourceType);
            result.put("equipmentType", actualEquipmentType);
            result.put("persistToKnowledgeBase", actualPersistToKnowledgeBase);
            result.put("userId", userId);

            Map<String, Object> message = new HashMap<>();
            message.put("docId", docId);
            message.put("sourceType", actualSourceType);
            message.put("equipmentType", actualEquipmentType);
            message.put("persistToKnowledgeBase", actualPersistToKnowledgeBase);
            message.put("fileName", file.getOriginalFilename());
            message.put("fileType", getFileExtension(file.getOriginalFilename()));
            message.put("userId", userId);
            message.put("filePath", filePath.toString());

            rabbitTemplate.convertAndSend(
                    AmqpConstants.QUEUE_DOCUMENT_PARSE,
                    message
            );

            log.info("Document uploaded successfully: {}, sourceType: {}, userId: {}", docId, actualSourceType, userId);
        } catch (IOException e) {
            log.error("Failed to upload document: {}", e.getMessage());
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    public void processDocument(Map<String, Object> message) {
        try {
            String docId = (String) message.get("docId");
            String sourceType = (String) message.getOrDefault("sourceType", "manual");
            String equipmentType = (String) message.get("equipmentType");
            Boolean persistToKnowledgeBase = (Boolean) message.getOrDefault("persistToKnowledgeBase", true);
            String fileName = (String) message.get("fileName");
            String userId = (String) message.get("userId");
            String filePathStr = (String) message.get("filePath");

            Path storageDir = getStorageDirectory();
            Path userDir = storageDir.resolve(userId != null ? userId : "anonymous");
            File documentFile = null;

            if (filePathStr != null && !filePathStr.isEmpty()) {
                documentFile = new File(filePathStr);
                if (!documentFile.exists()) {
                    log.warn("File not found at saved path: {}, falling back to search", filePathStr);
                    documentFile = null;
                }
            }

            if (documentFile == null) {
                File[] files = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId));
                if (files == null || files.length == 0) {
                    log.error("Document file not found in directory: {}, docId: {}", userDir, docId);
                    throw new IOException("Document file not found: " + docId);
                }
                documentFile = files[0];
            }

            log.info("Processing document: {}, file: {}", docId, documentFile.getName());

            String fileExtension = getFileExtension(documentFile.getName()).toLowerCase();
            String content = "";
            int pageCount = 0;

            switch (fileExtension) {
                case "pdf":
                    content = parsePdf(documentFile);
                    pageCount = countPdfPages(documentFile);
                    break;
                case "txt":
                    content = parseTxt(documentFile);
                    pageCount = 1;
                    break;
                case "docx":
                    content = parseDocx(documentFile);
                    pageCount = countDocxPages(documentFile);
                    break;
                case "xlsx":
                case "xls":
                    content = parseExcel(documentFile);
                    pageCount = 1;
                    break;
                case "csv":
                    content = parseCsv(documentFile);
                    pageCount = 1;
                    break;
                default:
                    throw new IOException("Unsupported file type: " + fileExtension);
            }

            Map<String, Object> structuredContent = extractStructuredContent(content);

            try {
                List<Map<String, Object>> paragraphs = new ArrayList<>();
                String[] contentLines = content.split("\n");
                for (int i = 0; i < contentLines.length; i++) {
                    if (!contentLines[i].trim().isEmpty()) {
                        Map<String, Object> paragraph = new HashMap<>();
                        paragraph.put("sectionTitle", "");
                        paragraph.put("pageNumber", 1);
                        paragraph.put("content", contentLines[i].trim());
                        paragraph.put("keywords", "");
                        paragraph.put("confidenceScore", 0.9);
                        paragraph.put("sourceType", sourceType);
                        paragraph.put("credibilityWeight", getCredibilityWeightBySourceType(sourceType));
                        paragraphs.add(paragraph);
                    }
                }

                Map<String, Object> vectorRequest = new HashMap<>();
                vectorRequest.put("docId", docId);
                vectorRequest.put("fileName", fileName);
                vectorRequest.put("fileType", fileExtension);
                vectorRequest.put("pageCount", pageCount);
                vectorRequest.put("equipmentType", equipmentType);
                vectorRequest.put("sourceType", sourceType);
                vectorRequest.put("credibilityWeight", getCredibilityWeightBySourceType(sourceType));
                vectorRequest.put("persistToKnowledgeBase", persistToKnowledgeBase);
                vectorRequest.put("userId", userId);
                vectorRequest.put("paragraphs", paragraphs);

                vectorStoreClient.processDocument(vectorRequest);
                log.info("Vector and metadata generated for docId: {}", docId);
            } catch (Exception e) {
                log.error("Failed to generate vector and metadata: {}", e.getMessage());
            }

            Map<String, Object> event = new HashMap<>();
            event.put("docId", docId);
            event.put("status", "processed");
            event.put("content", content);
            event.put("pageCount", pageCount);
            event.put("structuredContent", structuredContent);

            rabbitTemplate.convertAndSend(
                    AmqpConstants.EXCHANGE_DOCUMENT,
                    AmqpConstants.ROUTING_KEY_DOCUMENT_PARSED,
                    event
            );

            log.info("Document processed successfully: {}, page count: {}", docId, pageCount);

            Path parsedContentPath = userDir.resolve(docId + "_parsed.txt");
            Files.writeString(parsedContentPath, content);
            log.info("Parsed content saved to: {}", parsedContentPath);
        } catch (Exception e) {
            log.error("Failed to process document: {}", e.getMessage());
            String actualDocId = (String) message.get("docId");
            if (actualDocId != null) {
                Map<String, Object> event = new HashMap<>();
                event.put("docId", actualDocId);
                event.put("status", "error");
                event.put("message", e.getMessage());

                rabbitTemplate.convertAndSend(
                        AmqpConstants.EXCHANGE_DOCUMENT,
                        AmqpConstants.ROUTING_KEY_DOCUMENT_PARSED,
                        event
                );
            }
        }
    }

    @Override
    public Map<String, Object> getDocumentContent(String docId, String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path storageDir = getStorageDirectory();
            String effectiveUserId = userId != null ? userId : "anonymous";
            Path userDir = storageDir.resolve(effectiveUserId);

            Path parsedContentPath = userDir.resolve(docId + "_parsed.txt");
            String content = "";
            File originalFile = null;

            if (Files.exists(parsedContentPath)) {
                content = Files.readString(parsedContentPath);
                File[] originalFiles = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
                if (originalFiles != null && originalFiles.length > 0) {
                    originalFile = originalFiles[0];
                }
            } else {
                File[] files = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
                if (files != null && files.length > 0) {
                    originalFile = files[0];
                    String fileExtension = getFileExtension(originalFile.getName()).toLowerCase();

                    try {
                        switch (fileExtension) {
                            case "pdf":
                                content = parsePdf(originalFile);
                                break;
                            case "txt":
                                content = parseTxt(originalFile);
                                break;
                            case "docx":
                                content = parseDocx(originalFile);
                                break;
                            case "xlsx":
                            case "xls":
                                content = parseExcel(originalFile);
                                break;
                            case "csv":
                                content = parseCsv(originalFile);
                                break;
                            default:
                                content = "不支持的文件格式";
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse document on demand: {}", e.getMessage());
                        content = "文档解析失败: " + e.getMessage();
                    }
                } else if (!"anonymous".equals(effectiveUserId)) {
                    Path anonymousDir = storageDir.resolve("anonymous");
                    parsedContentPath = anonymousDir.resolve(docId + "_parsed.txt");
                    if (Files.exists(parsedContentPath)) {
                        content = Files.readString(parsedContentPath);
                    }
                    File[] anonFiles = anonymousDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
                    if (anonFiles != null && anonFiles.length > 0) {
                        originalFile = anonFiles[0];
                    }
                }
            }

            result.put("docId", docId);
            result.put("content", content);

            if (originalFile != null) {
                result.put("fileName", originalFile.getName());
            }

            return result;
        } catch (IOException e) {
            log.error("Failed to get document content: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getDocumentParagraphs(String docId, String userId) {
        try {
            Path storageDir = getStorageDirectory();
            String effectiveUserId = userId != null ? userId : "anonymous";
            Path userDir = storageDir.resolve(effectiveUserId);
            Path parsedContentPath = userDir.resolve(docId + "_parsed.txt");

            String content = "";
            if (Files.exists(parsedContentPath)) {
                content = Files.readString(parsedContentPath);
            } else {
                File[] files = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
                if (files != null && files.length > 0) {
                    File documentFile = files[0];
                    String fileExtension = getFileExtension(documentFile.getName()).toLowerCase();

                    try {
                        switch (fileExtension) {
                            case "pdf":
                                content = parsePdf(documentFile);
                                break;
                            case "txt":
                                content = parseTxt(documentFile);
                                break;
                            case "docx":
                                content = parseDocx(documentFile);
                                break;
                            case "xlsx":
                            case "xls":
                                content = parseExcel(documentFile);
                                break;
                            case "csv":
                                content = parseCsv(documentFile);
                                break;
                            default:
                                content = "";
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse document for paragraphs: {}", e.getMessage());
                    }
                } else if (!"anonymous".equals(effectiveUserId)) {
                    Path anonymousDir = storageDir.resolve("anonymous");
                    parsedContentPath = anonymousDir.resolve(docId + "_parsed.txt");
                    if (Files.exists(parsedContentPath)) {
                        content = Files.readString(parsedContentPath);
                    } else {
                        File[] anonFiles = anonymousDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
                        if (anonFiles != null && anonFiles.length > 0) {
                            File documentFile = anonFiles[0];
                            String fileExtension = getFileExtension(documentFile.getName()).toLowerCase();
                            try {
                                switch (fileExtension) {
                                    case "pdf":
                                        content = parsePdf(documentFile);
                                        break;
                                    case "txt":
                                        content = parseTxt(documentFile);
                                        break;
                                    case "docx":
                                        content = parseDocx(documentFile);
                                        break;
                                    case "xlsx":
                                    case "xls":
                                        content = parseExcel(documentFile);
                                        break;
                                    case "csv":
                                        content = parseCsv(documentFile);
                                        break;
                                    default:
                                        content = "";
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse anonymous document for paragraphs: {}", e.getMessage());
                            }
                        }
                    }
                }
            }

            List<Map<String, Object>> paragraphs = new ArrayList<>();
            String[] contentLines = content.split("\n");
            for (int i = 0; i < contentLines.length; i++) {
                if (!contentLines[i].trim().isEmpty()) {
                    Map<String, Object> paragraph = new HashMap<>();
                    paragraph.put("paragraphId", docId + "_p" + i);
                    paragraph.put("content", contentLines[i].trim());
                    paragraph.put("paragraphNumber", i);
                    paragraphs.add(paragraph);
                }
            }
            return paragraphs;
        } catch (IOException e) {
            log.error("Failed to get document paragraphs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getAllDocuments(String userId) {
        List<Map<String, Object>> documents = new ArrayList<>();
        try {
            Path storageDir = getStorageDirectory();
            if (!Files.exists(storageDir)) {
                return documents;
            }

            java.util.Set<String> addedDocIds = new java.util.HashSet<>();
            java.util.List<File> dirsToSearch = new java.util.ArrayList<>();

            if (userId == null || userId.isEmpty()) {
                File[] allDirs = storageDir.toFile().listFiles(File::isDirectory);
                if (allDirs != null) {
                    for (File dir : allDirs) {
                        if (!"anonymous".equals(dir.getName())) {
                            dirsToSearch.add(dir);
                        }
                    }
                    File anonDir = storageDir.resolve("anonymous").toFile();
                    if (anonDir.exists()) {
                        dirsToSearch.add(anonDir);
                    }
                }
            } else {
                Path userDir = storageDir.resolve(userId);
                if (userDir.toFile().exists()) {
                    dirsToSearch.add(userDir.toFile());
                }
                Path anonDir = storageDir.resolve("anonymous");
                if (anonDir.toFile().exists()) {
                    dirsToSearch.add(anonDir.toFile());
                }
            }

            for (File userDir : dirsToSearch) {
                String currentUserId = userDir.getName();
                File[] files = userDir.listFiles();
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    if (file.isFile() && !file.getName().endsWith("_parsed.txt")) {
                        String fileName = file.getName();
                        // 跳过 "doc_" 前缀，找下一个 "_" 的位置
                        int prefixEnd = fileName.startsWith("doc_") ? 4 : 0;
                        int underscoreIndex = fileName.indexOf('_', prefixEnd);
                        String docId = underscoreIndex > 0 ? fileName.substring(0, underscoreIndex) : fileName;
                        String uniqueKey = currentUserId + ":" + docId;

                        if (addedDocIds.contains(uniqueKey)) {
                            continue;
                        }
                        addedDocIds.add(uniqueKey);

                        String originalFileName = underscoreIndex > 0 ? fileName.substring(underscoreIndex + 1) : fileName;

                        Map<String, Object> doc = new HashMap<>();
                        doc.put("documentId", docId);
                        doc.put("fileName", originalFileName);
                        doc.put("fileType", getFileExtension(originalFileName));
                        doc.put("size", file.length());
                        doc.put("uploadTime", java.time.Instant.ofEpochMilli(file.lastModified()).toString());
                        doc.put("userId", currentUserId);

                        Path parsedContentPath = userDir.toPath().resolve(docId + "_parsed.txt");
                        doc.put("status", Files.exists(parsedContentPath) ? "已解析" : "解析中");

                        documents.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get all documents: {}", e.getMessage());
        }
        return documents;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex == -1 ? "" : fileName.substring(lastDotIndex + 1);
    }

    private String parsePdf(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageNum = 1; pageNum <= document.getNumberOfPages(); pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);
                if (!pageText.trim().isEmpty()) {
                    content.append("=== Page ").append(pageNum).append(" ===\n");
                    content.append(pageText.trim()).append("\n\n");
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse PDF: {}", e.getMessage());
            throw new IOException("PDF parsing failed: " + e.getMessage(), e);
        }
        return content.toString();
    }

    private int countPdfPages(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            log.error("Failed to count PDF pages: {}", e.getMessage());
            return 1;
        }
    }

    private String parseTxt(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private String parseDocx(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            List<XWPFTable> tables = document.getTables();
            for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                XWPFTable table = tables.get(tableIndex);
                content.append("\n=== Table ").append(tableIndex + 1).append(" ===\n");

                List<XWPFTableRow> rows = table.getRows();
                for (XWPFTableRow row : rows) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                        XWPFTableCell cell = cells.get(cellIndex);
                        String cellText = cell.getText().trim();
                        content.append(cellText);
                        if (cellIndex < cells.size() - 1) {
                            content.append("\t");
                        }
                    }
                    content.append("\n");
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse DOCX: {}", e.getMessage());
            throw new IOException("DOCX parsing failed: " + e.getMessage(), e);
        }
        return content.toString();
    }

    private int countDocxPages(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            int paragraphCount = document.getParagraphs().size();
            return Math.max(1, paragraphCount / 50);
        } catch (Exception e) {
            log.error("Failed to count DOCX pages: {}", e.getMessage());
            return 1;
        }
    }

    private String parseExcel(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        String fileExtension = getFileExtension(file.getName()).toLowerCase();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = "xlsx".equals(fileExtension) ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis)) {

            int numberOfSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");

                for (Row row : sheet) {
                    StringBuilder rowContent = new StringBuilder();
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        rowContent.append(cellValue).append("\t");
                    }
                    content.append(rowContent.toString().trim()).append("\n");
                }
                content.append("\n");
            }
        } catch (Exception e) {
            log.error("Failed to parse Excel: {}", e.getMessage());
            throw new IOException("Excel parsing failed: " + e.getMessage(), e);
        }
        return content.toString();
    }

    private String parseCsv(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        yield String.valueOf((long) numericValue);
                    } else {
                        yield String.valueOf(numericValue);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private Map<String, Object> extractStructuredContent(String content) {
        Map<String, Object> structuredContent = new HashMap<>();

        String[] pages = content.split("=== Page \\d+ ===");
        List<Map<String, Object>> pageContents = new ArrayList<>();

        for (int i = 0; i < pages.length; i++) {
            String pageContent = pages[i].trim();
            if (!pageContent.isEmpty()) {
                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageNumber", i + 1);
                pageInfo.put("content", pageContent);

                List<String> sections = extractSections(pageContent);
                pageInfo.put("sections", sections);

                pageContents.add(pageInfo);
            }
        }

        structuredContent.put("pages", pageContents);
        structuredContent.put("totalPages", pageContents.size());

        Map<String, Integer> keywords = extractKeywords(content);
        structuredContent.put("keywords", keywords);

        return structuredContent;
    }

    private List<String> extractSections(String content) {
        List<String> sections = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.matches("^\\d+.*") ||
                line.toLowerCase().contains("chapter") ||
                line.toLowerCase().contains("section") ||
                line.toLowerCase().contains("第") ||
                line.length() < 50 && line.matches(".*[\\.。]$")) {
                sections.add(line);
            }
        }

        return sections;
    }

    private Map<String, Integer> extractKeywords(String content) {
        Map<String, Integer> wordCount = new HashMap<>();
        String[] words = content.toLowerCase()
                .replaceAll("[^a-zA-Z\\u4e00-\\u9fa5]", " ")
                .split("\\s+");

        for (String word : words) {
            if (word.length() > 1) {
                wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
            }
        }

        return wordCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private double getCredibilityWeightBySourceType(String sourceType) {
        if (sourceType == null) {
            return 0.5;
        }
        return switch (sourceType) {
            case "industry_standard" -> 1.2;
            case "equipment_manual" -> 1.0;
            case "theory_paper" -> 0.9;
            case "maintenance_record" -> 0.8;
            case "user_feedback" -> 0.6;
            default -> 0.5;
        };
    }

    @Override
    public boolean deleteDocument(String docId, String userId) {
        log.info("Deleting document: docId={}, userId={}", docId, userId);
        try {
            Path storageDir = getStorageDirectory();
            String effectiveUserId = userId != null ? userId : "anonymous";
            Path userDir = storageDir.resolve(effectiveUserId);

            boolean deleted = false;

            Path parsedContentPath = userDir.resolve(docId + "_parsed.txt");
            if (Files.exists(parsedContentPath)) {
                Files.delete(parsedContentPath);
                log.info("Deleted parsed content: {}", parsedContentPath);
                deleted = true;
            }

            File[] files = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.delete()) {
                        log.info("Deleted original file: {}", file.getName());
                        deleted = true;
                    } else {
                        log.warn("Failed to delete file: {}", file.getName());
                    }
                }
            }

            if (!deleted && !"anonymous".equals(effectiveUserId)) {
                Path anonDir = storageDir.resolve("anonymous");
                if (anonDir.toFile().exists()) {
                    parsedContentPath = anonDir.resolve(docId + "_parsed.txt");
                    if (Files.exists(parsedContentPath)) {
                        Files.delete(parsedContentPath);
                        log.info("Deleted anonymous parsed content: {}", parsedContentPath);
                        deleted = true;
                    }
                    File[] anonFiles = anonDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt"));
                    if (anonFiles != null) {
                        for (File file : anonFiles) {
                            if (file.delete()) {
                                log.info("Deleted anonymous original file: {}", file.getName());
                                deleted = true;
                            }
                        }
                    }
                }
            }

            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete document: docId={}, error={}", docId, e.getMessage(), e);
            return false;
        }
    }
}

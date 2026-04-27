package com.cxyaqcdm.fta.document.service.impl;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.document.client.ClassificationClient;
import com.cxyaqcdm.fta.document.client.KnowledgeGraphClient;
import com.cxyaqcdm.fta.document.client.RAGServiceClient;
import com.cxyaqcdm.fta.document.client.VectorStoreClient;
import com.cxyaqcdm.fta.document.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ClassificationClient classificationClient;
    private final RAGServiceClient ragServiceClient;
    private final KnowledgeGraphClient knowledgeGraphClient;

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
        log.info("========================================================");
        log.info("★★☆ uploadDocument 开始 ☆★★");
        log.info("文件名: {}, sourceType: {}, equipmentType: {}, persistToKnowledgeBase: {}, userId: {}",
                file.getOriginalFilename(), sourceType, equipmentType, persistToKnowledgeBase, userId);
        log.info("========================================================");

        try {
            String docId = "doc_" + UUID.randomUUID().toString().replace("-", "");
            log.info("[Upload Step 1] 生成 docId: {}", docId);

            Path storageDir = getStorageDirectory();
            log.info("[Upload Step 2] 存储目录: {}", storageDir);

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

            String actualSourceType = sourceType;
            String actualEquipmentType = (equipmentType != null && !equipmentType.isEmpty()) ? equipmentType : null;
            Boolean actualPersistToKnowledgeBase = persistToKnowledgeBase != null ? persistToKnowledgeBase : true;

            if (sourceType == null || sourceType.isEmpty() || "auto".equalsIgnoreCase(sourceType) || "unknown".equalsIgnoreCase(sourceType)) {
                log.info("Source type not specified, performing automatic classification for: {}", file.getOriginalFilename());
                try {
                    String contentPreview = extractContentPreview(filePath.toFile(), getFileExtension(file.getOriginalFilename()));
                    if (contentPreview != null && contentPreview.length() > 50) {
                        ClassificationClient.ClassificationResult classificationResult =
                                classificationClient.classifyDocument(file.getOriginalFilename(), contentPreview);
                        if (classificationResult != null) {
                            actualSourceType = classificationResult.getSourceType();
                            result.put("classificationConfidence", classificationResult.getConfidence());
                            result.put("classificationReasoning", classificationResult.getReasoning());
                            result.put("classificationMethod", classificationResult.getMethod());
                            result.put("classificationCredibilityWeight", classificationResult.getCredibilityWeight());
                            log.info("Document auto-classified: sourceType={}, confidence={}, method={}",
                                    actualSourceType, classificationResult.getConfidence(), classificationResult.getMethod());
                        } else {
                            actualSourceType = "unknown";
                        }
                    } else {
                        actualSourceType = "unknown";
                        log.warn("Content preview too short for classification");
                    }
                } catch (Exception e) {
                    log.error("Failed to classify document: {}", e.getMessage());
                    actualSourceType = "unknown";
                }
            } else {
                log.info("Using user-provided source type: {}", sourceType);
            }

            if (actualSourceType == null || actualSourceType.isEmpty()) {
                actualSourceType = "unknown";
            }

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

            log.info("[Upload Step 3] 发送消息到队列: EXCHANGE_DOCUMENT / ROUTING_KEY_DOCUMENT_PARSE_REQUEST");
            log.info("[Upload Step 3] 消息内容: docId={}, filePath={}, sourceType={}, userId={}",
                    docId, filePath.toString(), actualSourceType, userId);

            rabbitTemplate.convertAndSend(
                    AmqpConstants.EXCHANGE_DOCUMENT,
                    AmqpConstants.ROUTING_KEY_DOCUMENT_PARSE_REQUEST,
                    message
            );

            log.info("[Upload Step 3] ✓ 消息已发送到队列");

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", docId);
            metadata.put("sourceType", actualSourceType);
            metadata.put("equipmentType", actualEquipmentType);
            metadata.put("persistToKnowledgeBase", actualPersistToKnowledgeBase);
            metadata.put("fileName", file.getOriginalFilename());
            metadata.put("fileType", getFileExtension(file.getOriginalFilename()));
            metadata.put("userId", userId);
            metadata.put("uploadTime", java.time.Instant.now().toString());
            metadata.put("classificationConfidence", result.get("classificationConfidence"));
            metadata.put("classificationReasoning", result.get("classificationReasoning"));
            metadata.put("classificationMethod", result.get("classificationMethod"));
            double credibilityWeight = getCredibilityWeightBySourceType(actualSourceType);
            metadata.put("credibilityWeight", credibilityWeight);
            saveDocumentMetadata(userDir, docId, metadata);

            log.info("Document uploaded successfully: {}, sourceType: {}, userId: {}", docId, actualSourceType, userId);
        } catch (IOException e) {
            log.error("Failed to upload document: {}", e.getMessage());
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    private void saveDocumentMetadata(Path userDir, String docId, Map<String, Object> metadata) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Path metadataPath = userDir.resolve(docId + "_metadata.json");
            objectMapper.writeValue(metadataPath.toFile(), metadata);
            log.info("Saved document metadata: {}", metadataPath);
        } catch (Exception e) {
            log.error("Failed to save document metadata: {}", e.getMessage());
        }
    }

    private Map<String, Object> loadDocumentMetadata(Path userDir, String docId) {
        try {
            Path metadataPath = userDir.resolve(docId + "_metadata.json");
            if (Files.exists(metadataPath)) {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(metadataPath.toFile(), Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to load document metadata: {}", e.getMessage());
        }
        return null;
    }

    private void saveDocumentMetadataToDatabase(Map<String, Object> metadata) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("docId", metadata.get("docId"));
            request.put("fileName", metadata.get("fileName"));
            request.put("fileType", metadata.get("fileType"));
            request.put("pageCount", 0);
            request.put("sourceType", metadata.get("sourceType"));
            request.put("equipmentType", metadata.get("equipmentType"));
            request.put("persistToKnowledgeBase", metadata.get("persistToKnowledgeBase"));
            request.put("userId", metadata.get("userId"));
            request.put("status", "PENDING");

            vectorStoreClient.createDocumentMetadata(request);
            log.info("Document metadata saved to database: docId={}", metadata.get("docId"));
        } catch (Exception e) {
            log.error("Failed to save document metadata to database: {}", e.getMessage());
        }
    }

    private String extractContentPreview(File file, String fileExtension) {
        try {
            String content = "";
            switch (fileExtension.toLowerCase()) {
                case "pdf":
                    content = parsePdf(file);
                    break;
                case "txt":
                    content = parseTxt(file);
                    break;
                case "docx":
                    content = parseDocx(file);
                    break;
                case "xlsx":
                case "xls":
                    content = parseExcel(file);
                    break;
                case "csv":
                    content = parseCsv(file);
                    break;
                default:
                    return null;
            }
            return content.length() > 800 ? content.substring(0, 800) : content;
        } catch (Exception e) {
            log.error("Failed to extract content preview: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void processDocument(Map<String, Object> message) {
        log.info("========================================================");
        log.info("★★☆ processDocument 开始 ☆★★");
        log.info("收到消息 keys: {}", message.keySet());
        log.info("========================================================");

        try {
            String docId = (String) message.get("docId");
            String sourceType = (String) message.getOrDefault("sourceType", "manual");
            String equipmentType = (String) message.get("equipmentType");
            Boolean persistToKnowledgeBase = (Boolean) message.getOrDefault("persistToKnowledgeBase", true);
            String fileName = (String) message.get("fileName");
            String userId = (String) message.get("userId");
            String filePathStr = (String) message.get("filePath");

            log.info("[Step 1] 解析消息参数完成: docId={}, sourceType={}, equipmentType={}, userId={}",
                    docId, sourceType, equipmentType, userId);

            Path storageDir = getStorageDirectory();
            Path userDir = storageDir.resolve(userId != null ? userId : "anonymous");
            File documentFile = null;

            log.info("[Step 2] 存储目录: userDir={}", userDir);

            if (filePathStr != null && !filePathStr.isEmpty()) {
                documentFile = new File(filePathStr);
                log.info("[Step 2a] 使用提供的文件路径: {}", filePathStr);
                if (!documentFile.exists()) {
                    log.warn("File not found at saved path: {}, falling back to search", filePathStr);
                    documentFile = null;
                }
            }

            if (documentFile == null) {
                log.info("[Step 2b] 搜索文件 in userDir: {}", userDir);
                File[] files = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId));
                if (files == null || files.length == 0) {
                    log.error("Document file not found in directory: {}, docId: {}", userDir, docId);
                    throw new IOException("Document file not found: " + docId);
                }
                documentFile = files[0];
            }

            log.info("[Step 3] 找到文件: {}, 大小: {} bytes", documentFile.getName(), documentFile.length());

            log.info("Processing document: {}, file: {}", docId, documentFile.getName());

            String fileExtension = getFileExtension(documentFile.getName()).toLowerCase();
            log.info("[Step 4] 文件扩展名: {}", fileExtension);

            String content = "";
            int pageCount = 0;

            log.info("[Step 5] 开始解析文件内容...");
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
            log.info("[Step 5] 文件解析完成! content长度: {}, pageCount: {}", content.length(), pageCount);

            Map<String, Object> structuredContent = extractStructuredContent(content);

            try {
                List<Map<String, Object>> paragraphs = new ArrayList<>();
                if (content != null && !content.trim().isEmpty()) {
                    Map<String, Object> paragraph = new HashMap<>();
                    paragraph.put("sectionTitle", "");
                    paragraph.put("pageNumber", 1);
                    paragraph.put("content", content);
                    paragraph.put("keywords", "");
                    paragraph.put("confidenceScore", 0.9);
                    paragraph.put("sourceType", sourceType);
                    paragraph.put("credibilityWeight", getCredibilityWeightBySourceType(sourceType));
                    paragraphs.add(paragraph);
                }
                log.info("[Step 6] 生成段落数: {}, 发送完整内容由Python语义分块", paragraphs.size());

                Map<String, Object> syncRequest = new HashMap<>();
                syncRequest.put("docId", docId);
                syncRequest.put("fileName", fileName);
                syncRequest.put("fileType", fileExtension);
                syncRequest.put("pageCount", pageCount);
                syncRequest.put("userId", userId);
                syncRequest.put("sourceType", sourceType);
                syncRequest.put("equipmentType", equipmentType);
                syncRequest.put("credibilityWeight", getCredibilityWeightBySourceType(sourceType));
                syncRequest.put("persistToKnowledgeBase", persistToKnowledgeBase);
                syncRequest.put("paragraphs", paragraphs);

                log.info("[Step 7] 调用 ragServiceClient.syncVectorsToChroma (语义分块 & 向量存储)...");
                log.info("[Step 7] syncRequest: docId={}, userId={}, paragraphs数量={}", docId, userId, paragraphs.size());
                if (paragraphs.size() > 0) {
                    log.info("[Step 7] 第一个段落内容预览: {}", paragraphs.get(0).get("content"));
                }

                Map<String, Object> syncResponse = ragServiceClient.syncVectorsToChroma(syncRequest);
                log.info("[Step 7] ✓ Chroma同步响应: {}", syncResponse);
            } catch (Exception e) {
                log.error("[Step 7] ✗ Chroma同步失败: {}, 异常类型: {}", e.getMessage(), e.getClass().getName());
            }

            try {
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
            } catch (Exception e) {
                log.error("Failed to send document processed event: {}", e.getMessage());
            }

            log.info("Document processed successfully: {}, page count: {}", docId, pageCount);

            Path parsedContentPath = userDir.resolve(docId + "_parsed.txt");
            Files.writeString(parsedContentPath, content);
            log.info("Parsed content saved to: {}", parsedContentPath);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceType", sourceType);
            metadata.put("credibilityWeight", getCredibilityWeightBySourceType(sourceType));
            metadata.put("processedTime", java.time.Instant.now().toString());
            ObjectMapper metadataMapper = new ObjectMapper();
            Path metadataPath = userDir.resolve(docId + "_metadata.json");
            metadataMapper.writeValue(metadataPath.toFile(), metadata);
            log.info("Document metadata saved to: {}", metadataPath);
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

            Map<String, Map<String, Object>> dbMetadataCache = new java.util.HashMap<>();
            try {
                List<Map<String, Object>> dbDocuments = vectorStoreClient.getAllDocumentMetadata(userId);
                if (dbDocuments != null) {
                    for (Map<String, Object> dbDoc : dbDocuments) {
                        String docId = (String) dbDoc.get("docId");
                        if (docId != null) {
                            dbMetadataCache.put(docId, dbDoc);
                        }
                    }
                    log.info("Loaded {} document metadata from database", dbDocuments.size());
                }
            } catch (Exception e) {
                log.warn("Failed to load document metadata from database: {}", e.getMessage());
            }

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
                    if (file.isFile() && !file.getName().endsWith("_parsed.txt") && !file.getName().endsWith("_metadata.json")) {
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

                        Map<String, Object> dbMetadata = dbMetadataCache.get(docId);
                        if (dbMetadata != null) {
                            doc.put("sourceType", dbMetadata.getOrDefault("sourceType", "unknown"));
                            doc.put("credibilityWeight", dbMetadata.getOrDefault("credibilityWeight", 0.5));
                            doc.put("equipmentType", dbMetadata.get("equipmentType"));
                            doc.put("pageCount", dbMetadata.get("pageCount"));
                            if (dbMetadata.get("uploadTime") != null) {
                                doc.put("uploadTime", dbMetadata.get("uploadTime"));
                            }
                        } else {
                            Path metadataPath = userDir.toPath().resolve(docId + "_metadata.json");
                            if (Files.exists(metadataPath)) {
                                try {
                                    ObjectMapper metadataMapper = new ObjectMapper();
                                    Map<String, Object> metadata = metadataMapper.readValue(metadataPath.toFile(), Map.class);
                                    doc.put("sourceType", metadata.getOrDefault("sourceType", "unknown"));
                                    doc.put("credibilityWeight", metadata.getOrDefault("credibilityWeight", 0.5));
                                } catch (Exception e) {
                                    log.warn("Failed to read metadata for docId: {}, using defaults", docId);
                                    doc.put("sourceType", "unknown");
                                    doc.put("credibilityWeight", 0.5);
                                }
                            } else {
                                doc.put("sourceType", "unknown");
                                doc.put("credibilityWeight", 0.5);
                            }
                        }

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
                content.append("\n");

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

        List<Map<String, Object>> pageContents = new ArrayList<>();

        // 现在不按页面分割，把整个内容作为一个页面
        if (content != null && !content.trim().isEmpty()) {
            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("pageNumber", 1);
            pageInfo.put("content", content.trim());

            List<String> sections = extractSections(content);
            pageInfo.put("sections", sections);

            pageContents.add(pageInfo);
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
            case "mixed_collection" -> 0.0;
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

            Path metadataPath = userDir.resolve(docId + "_metadata.json");
            if (Files.exists(metadataPath)) {
                Files.delete(metadataPath);
                log.info("Deleted metadata file: {}", metadataPath);
            }

            File[] files = userDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt") && !name.endsWith("_metadata.json"));
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
                    metadataPath = anonDir.resolve(docId + "_metadata.json");
                    if (Files.exists(metadataPath)) {
                        Files.delete(metadataPath);
                        log.info("Deleted anonymous metadata file: {}", metadataPath);
                    }
                    File[] anonFiles = anonDir.toFile().listFiles((dir, name) -> name.startsWith(docId) && !name.endsWith("_parsed.txt") && !name.endsWith("_metadata.json"));
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

            try {
                vectorStoreClient.deleteDocumentMetadata(docId);
                log.info("Deleted vector store metadata for docId: {}", docId);
            } catch (Exception e) {
                log.error("Failed to delete vector store metadata for docId: {}, error: {}", docId, e.getMessage());
            }

            try {
                ragServiceClient.deleteVectors(docId, effectiveUserId);
                log.info("Deleted Chroma vectors for docId: {}", docId);
            } catch (Exception e) {
                log.error("Failed to delete Chroma vectors for docId: {}, error: {}", docId, e.getMessage());
            }

            try {
                knowledgeGraphClient.deleteUserDocumentKnowledge(effectiveUserId, docId);
                log.info("Deleted knowledge graph data for docId: {}", docId);
            } catch (Exception e) {
                log.error("Failed to delete knowledge graph data for docId: {}, error: {}", docId, e.getMessage());
            }

            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete document: docId={}, error={}", docId, e.getMessage(), e);
            return false;
        }
    }
}

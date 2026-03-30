package com.cxyaqcdm.fta.document.service.impl;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.document.client.VectorStoreClient;
import com.cxyaqcdm.fta.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final RabbitTemplate rabbitTemplate;
    private final VectorStoreClient vectorStoreClient;

    @Value("${document.storage.path}")
    private String storagePath;

    private String currentSourceType = "unknown";
    private String currentEquipmentType = null;
    private Boolean currentPersistToKnowledgeBase = false;
    private String currentDocId = null;

    @Override
    public Map<String, Object> uploadDocument(MultipartFile file, String sourceType, String equipmentType, Boolean persistToKnowledgeBase) {
        Map<String, Object> result = new HashMap<>();
        try {
            String docId = "doc_" + UUID.randomUUID().toString().replace("-", "");

            Path storageDir = Paths.get(storagePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            String fileName = docId + "_" + file.getOriginalFilename();
            Path filePath = storageDir.resolve(fileName);
            file.transferTo(filePath.toFile());

            this.currentSourceType = sourceType != null ? sourceType : "unknown";
            this.currentEquipmentType = equipmentType;
            this.currentPersistToKnowledgeBase = persistToKnowledgeBase != null ? persistToKnowledgeBase : false;
            this.currentDocId = docId;

            result.put("docId", docId);
            result.put("status", "queued");
            result.put("pageCount", 0);
            result.put("sourceType", this.currentSourceType);
            result.put("equipmentType", this.currentEquipmentType);
            result.put("persistToKnowledgeBase", this.currentPersistToKnowledgeBase);

            Map<String, Object> message = new HashMap<>();
            message.put("docId", docId);
            message.put("sourceType", this.currentSourceType);
            message.put("equipmentType", this.currentEquipmentType);
            message.put("persistToKnowledgeBase", this.currentPersistToKnowledgeBase);
            message.put("fileName", file.getOriginalFilename());
            message.put("fileType", getFileExtension(file.getOriginalFilename()));

            rabbitTemplate.convertAndSend(
                    AmqpConstants.QUEUE_DOCUMENT_PARSE,
                    message
            );

            log.info("Document uploaded successfully: {}, sourceType: {}", docId, this.currentSourceType);
        } catch (IOException e) {
            log.error("Failed to upload document: {}", e.getMessage());
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processDocument(Object messageObj) {
        try {
            Map<String, Object> message = (Map<String, Object>) messageObj;
            String docId = (String) message.get("docId");
            String sourceType = (String) message.getOrDefault("sourceType", "unknown");
            String equipmentType = (String) message.get("equipmentType");
            Boolean persistToKnowledgeBase = (Boolean) message.getOrDefault("persistToKnowledgeBase", false);
            String fileName = (String) message.get("fileName");

            Path storageDir = Paths.get(storagePath);
            File[] files = storageDir.toFile().listFiles((dir, name) -> name.startsWith(docId));
            if (files == null || files.length == 0) {
                throw new IOException("Document file not found: " + docId);
            }
            File documentFile = files[0];

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
        } catch (Exception e) {
            log.error("Failed to process document: {}", e.getMessage());
            Map<String, Object> event = new HashMap<>();
            event.put("docId", "unknown");
            event.put("status", "error");
            event.put("message", e.getMessage());

            rabbitTemplate.convertAndSend(
                    AmqpConstants.EXCHANGE_DOCUMENT,
                    AmqpConstants.ROUTING_KEY_DOCUMENT_PARSED,
                    event
            );
        }
    }

    @Override
    public Map<String, Object> getDocumentContent(String docId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path storageDir = Paths.get(storagePath);
            File[] files = storageDir.toFile().listFiles((dir, name) -> name.startsWith(docId));
            if (files == null || files.length == 0) {
                return null;
            }
            File documentFile = files[0];
            String content = Files.readString(documentFile.toPath());

            result.put("docId", docId);
            result.put("content", content);
            result.put("fileName", documentFile.getName());

            return result;
        } catch (IOException e) {
            log.error("Failed to get document content: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getDocumentParagraphs(String docId) {
        try {
            Path storageDir = Paths.get(storagePath);
            File[] files = storageDir.toFile().listFiles((dir, name) -> name.startsWith(docId));
            if (files == null || files.length == 0) {
                return new ArrayList<>();
            }
            File documentFile = files[0];
            String content = Files.readString(documentFile.toPath());

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

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex == -1 ? "" : fileName.substring(lastDotIndex + 1);
    }

    private String parsePdf(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按页提取文本
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
            return 1; // 默认返回1页
        }
    }

    private String parseTxt(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private String parseDocx(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            // 提取段落
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph paragraph = paragraphs.get(i);
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            // 提取表格
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
            // DOCX 没有直接的页数信息，这里返回一个估算值
            // 基于段落数量估算，每50个段落算一页
            int paragraphCount = document.getParagraphs().size();
            return Math.max(1, paragraphCount / 50);
        } catch (Exception e) {
            log.error("Failed to count DOCX pages: {}", e.getMessage());
            return 1;
        }
    }

    private Map<String, Object> extractStructuredContent(String content) {
        Map<String, Object> structuredContent = new HashMap<>();

        // 按页分割内容
        String[] pages = content.split("=== Page \\d+ ===");
        List<Map<String, Object>> pageContents = new ArrayList<>();

        for (int i = 0; i < pages.length; i++) {
            String pageContent = pages[i].trim();
            if (!pageContent.isEmpty()) {
                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageNumber", i + 1);
                pageInfo.put("content", pageContent);

                // 提取章节标题（简单的启发式方法）
                List<String> sections = extractSections(pageContent);
                pageInfo.put("sections", sections);

                pageContents.add(pageInfo);
            }
        }

        structuredContent.put("pages", pageContents);
        structuredContent.put("totalPages", pageContents.size());

        // 提取关键词（简单的词频统计）
        Map<String, Integer> keywords = extractKeywords(content);
        structuredContent.put("keywords", keywords);

        return structuredContent;
    }

    private List<String> extractSections(String content) {
        List<String> sections = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim();
            // 简单的章节识别：以数字开头或包含特定关键词的行
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
            if (word.length() > 1) { // 忽略单字
                wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
            }
        }

        // 返回出现频率最高的10个词
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
}
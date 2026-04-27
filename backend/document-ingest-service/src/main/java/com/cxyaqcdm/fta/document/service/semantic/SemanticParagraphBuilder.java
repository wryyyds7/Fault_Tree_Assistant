package com.cxyaqcdm.fta.document.service.semantic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SemanticParagraphBuilder {

    private final DomainEntityExtractor entityExtractor;
    private final CausalRelationExtractor causalExtractor;

    private static final List<SectionPattern> SECTION_PATTERNS = new ArrayList<>();
    private static final int MIN_PARAGRAPH_LENGTH = 10;
    private static final int MAX_PARAGRAPH_LENGTH = 500;

    static {
        SECTION_PATTERNS.add(new SectionPattern(Pattern.compile("^第[一二三四五六七八九十百千\\d]+[章节篇]"), 1));
        SECTION_PATTERNS.add(new SectionPattern(Pattern.compile("^[一二三四五六七八九十]+[、\\.]"), 2));
        SECTION_PATTERNS.add(new SectionPattern(Pattern.compile("^\\d+[\\.、]"), 3));
        SECTION_PATTERNS.add(new SectionPattern(Pattern.compile("^[A-Z][\\.、]"), 4));
        SECTION_PATTERNS.add(new SectionPattern(Pattern.compile("^\\(\\d+\\)"), 5));
    }

    public List<SemanticParagraph> buildSemanticParagraphs(String rawText, String sourceType, int startPage) {
        List<SemanticParagraph> result = new ArrayList<>();

        String cleanedText = preprocessText(rawText);
        List<SectionInfo> sections = detectSections(cleanedText);
        List<TextBlock> textBlocks = splitIntoBlocks(cleanedText, sections);

        int paragraphIndex = 0;
        for (TextBlock block : textBlocks) {
            if (block.text.trim().length() < MIN_PARAGRAPH_LENGTH) {
                continue;
            }

            List<SemanticParagraph.DomainEntity> entities = entityExtractor.extractEntities(block.text);
            List<SemanticParagraph.CausalTriple> causalRelations = causalExtractor.extractCausalRelations(block.text);

            if (entities.isEmpty() && causalRelations.isEmpty()) {
                if (block.text.trim().length() > 30) {
                    SemanticParagraph paragraph = buildBasicParagraph(block, paragraphIndex++, sourceType);
                    result.add(paragraph);
                }
            } else {
                SemanticParagraph paragraph = SemanticParagraph.builder()
                        .paragraphId(UUID.randomUUID().toString())
                        .content(block.text.trim())
                        .pageNumber(block.pageNumber > 0 ? block.pageNumber : startPage)
                        .sectionTitle(block.sectionTitle)
                        .paragraphIndex(paragraphIndex++)
                        .confidenceScore(calculateParagraphConfidence(entities, causalRelations))
                        .entities(entities)
                        .causalRelations(causalRelations)
                        .sourceType(sourceType)
                        .credibilityWeight(getCredibilityWeight(sourceType))
                        .build();
                result.add(paragraph);
            }
        }

        return result;
    }

    private String preprocessText(String text) {
        return text
                .replaceAll("\\r\\n|\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("([\\u4e00-\\u9fa5])[ \t]+([\\u4e00-\\u9fa5])", "$1$2")
                .trim();
    }

    private List<SectionInfo> detectSections(String text) {
        List<SectionInfo> sections = new ArrayList<>();
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            for (SectionPattern sp : SECTION_PATTERNS) {
                Matcher matcher = sp.pattern.matcher(line);
                if (matcher.find() && line.length() < 50) {
                    sections.add(new SectionInfo(line, i, sp.level));
                    break;
                }
            }
        }

        return sections;
    }

    private List<TextBlock> splitIntoBlocks(String text, List<SectionInfo> sections) {
        List<TextBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\n");

        StringBuilder currentBlock = new StringBuilder();
        String currentSection = "";
        int currentPage = 1;
        int blockStartLine = 0;

        Pattern pagePattern = Pattern.compile("=== Page (\\d+) ===");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                if (currentBlock.length() > 0 && currentBlock.toString().trim().length() >= MIN_PARAGRAPH_LENGTH) {
                    blocks.add(new TextBlock(currentBlock.toString().trim(), currentSection, currentPage, blockStartLine));
                    currentBlock = new StringBuilder();
                }
                continue;
            }

            Matcher pageMatcher = pagePattern.matcher(line);
            if (pageMatcher.find()) {
                currentPage = Integer.parseInt(pageMatcher.group(1));
                if (currentBlock.length() > 0) {
                    blocks.add(new TextBlock(currentBlock.toString().trim(), currentSection, currentPage, blockStartLine));
                    currentBlock = new StringBuilder();
                }
                continue;
            }

            final int currentLine = i;
            boolean isSection = sections.stream().anyMatch(s -> s.lineNumber == currentLine);

            if (isSection && currentBlock.length() > 0) {
                blocks.add(new TextBlock(currentBlock.toString().trim(), currentSection, currentPage, blockStartLine));
                currentBlock = new StringBuilder();
                currentSection = line;
                blockStartLine = i;
            } else if (isSection) {
                currentSection = line;
                blockStartLine = i;
            } else {
                if (currentBlock.length() > 0) {
                    currentBlock.append("\n");
                }
                currentBlock.append(line);

                if (currentBlock.length() > MAX_PARAGRAPH_LENGTH && line.endsWith("。")) {
                    blocks.add(new TextBlock(currentBlock.toString().trim(), currentSection, currentPage, blockStartLine));
                    currentBlock = new StringBuilder();
                }
            }
        }

        if (currentBlock.length() > 0 && currentBlock.toString().trim().length() >= MIN_PARAGRAPH_LENGTH) {
            blocks.add(new TextBlock(currentBlock.toString().trim(), currentSection, currentPage, blockStartLine));
        }

        return blocks;
    }

    private SemanticParagraph buildBasicParagraph(TextBlock block, int index, String sourceType) {
        return SemanticParagraph.builder()
                .paragraphId(UUID.randomUUID().toString())
                .content(block.text.trim())
                .pageNumber(block.pageNumber)
                .sectionTitle(block.sectionTitle)
                .paragraphIndex(index)
                .confidenceScore(0.5)
                .entities(Collections.emptyList())
                .causalRelations(Collections.emptyList())
                .sourceType(sourceType)
                .credibilityWeight(getCredibilityWeight(sourceType))
                .build();
    }

    private double calculateParagraphConfidence(
            List<SemanticParagraph.DomainEntity> entities,
            List<SemanticParagraph.CausalTriple> relations) {
        if (entities.isEmpty() && relations.isEmpty()) {
            return 0.3;
        }

        double entityScore = Math.min(0.5, entities.size() * 0.1);
        double relationScore = Math.min(0.5, relations.size() * 0.15);

        double avgEntityConfidence = entities.stream()
                .mapToDouble(SemanticParagraph.DomainEntity::getConfidence)
                .average()
                .orElse(0.5);

        double avgRelationConfidence = relations.stream()
                .mapToDouble(SemanticParagraph.CausalTriple::getConfidence)
                .average()
                .orElse(0.5);

        return (entityScore * avgEntityConfidence + relationScore * avgRelationConfidence) /
               (entityScore + relationScore + 0.001);
    }

    private double getCredibilityWeight(String sourceType) {
        if (sourceType == null) return 0.5;
        return switch (sourceType) {
            case "industry_standard" -> 1.2;
            case "equipment_manual" -> 1.0;
            case "theory_paper" -> 0.9;
            case "maintenance_record" -> 0.8;
            case "user_feedback" -> 0.6;
            default -> 0.5;
        };
    }

    public Map<String, Object> extractStructuredContent(String rawText, String sourceType, int pageCount) {
        List<SemanticParagraph> paragraphs = buildSemanticParagraphs(rawText, sourceType, 1);

        Map<String, Object> result = new HashMap<>();
        result.put("paragraphs", paragraphs);
        result.put("totalParagraphs", paragraphs.size());
        result.put("totalEntities", paragraphs.stream().mapToInt(p -> p.getEntities().size()).sum());
        result.put("totalRelations", paragraphs.stream().mapToInt(p -> p.getCausalRelations().size()).sum());
        result.put("pageCount", pageCount);

        Map<String, Long> entityDistribution = paragraphs.stream()
                .flatMap(p -> p.getEntities().stream())
                .collect(Collectors.groupingBy(
                        e -> e.getType().name(),
                        Collectors.counting()
                ));
        result.put("entityDistribution", entityDistribution);

        Map<String, Long> relationDistribution = paragraphs.stream()
                .flatMap(p -> p.getCausalRelations().stream())
                .collect(Collectors.groupingBy(
                        SemanticParagraph.CausalTriple::getRelationType,
                        Collectors.counting()
                ));
        result.put("relationDistribution", relationDistribution);

        return result;
    }

    private static class SectionInfo {
        String title;
        int lineNumber;
        int level;

        SectionInfo(String title, int lineNumber, int level) {
            this.title = title;
            this.lineNumber = lineNumber;
            this.level = level;
        }
    }

    private static class SectionPattern {
        Pattern pattern;
        int level;

        SectionPattern(Pattern pattern, int level) {
            this.pattern = pattern;
            this.level = level;
        }
    }

    private static class TextBlock {
        String text;
        String sectionTitle;
        int pageNumber;
        int startLine;

        TextBlock(String text, String sectionTitle, int pageNumber, int startLine) {
            this.text = text;
            this.sectionTitle = sectionTitle;
            this.pageNumber = pageNumber;
            this.startLine = startLine;
        }
    }
}
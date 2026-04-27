package com.cxyaqcdm.fta.validation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.common.enums.LogicGateEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
public class AIAnalysisClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.analysis.service.url:http://localhost:8001}")
    private String aiServiceUrl;

    @Value("${ai.analysis.enabled:true}")
    private boolean aiAnalysisEnabled;

    public AIAnalysisClient(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public String analyzeFaultTree(FaultTreeDTO faultTree, List<Map<String, Object>> validationErrors) {
        if (!aiAnalysisEnabled) {
            log.info("AI analysis is disabled, using fallback analysis");
            return generateFallbackAnalysis(faultTree, validationErrors);
        }

        try {
            log.info("Requesting AI analysis for fault tree: {}", faultTree.getTreeId());

            String prompt = buildAnalysisPrompt(faultTree, validationErrors);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("systemPrompt", "你是一位专业的工业故障树分析(FTA)专家。请根据提供的故障树结构和验证结果，给出专业的分析和建议。");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/api/v1/chat/analyze-fault-tree";
            log.info("Calling AI analysis service at: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Successfully received AI analysis");
                return parseAIResponse(response.getBody());
            } else {
                log.warn("AI analysis failed with status: {}, using fallback analysis", response.getStatusCode());
                return generateFallbackAnalysis(faultTree, validationErrors);
            }
        } catch (Exception e) {
            log.error("Error during AI analysis: {}, using fallback analysis", e.getMessage());
            return generateFallbackAnalysis(faultTree, validationErrors);
        }
    }

    private String generateFallbackAnalysis(FaultTreeDTO faultTree, List<Map<String, Object>> validationErrors) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("【故障树智能分析报告】\n\n");
        
        // 基本结构分析
        analysis.append("1. 结构评价：\n");
        int nodeCount = countNodes(faultTree);
        int depth = calculateDepth(faultTree);
        
        if (nodeCount < 3) {
            analysis.append("  - 故障树节点过少，建议进一步细化分析\n");
        } else if (nodeCount > 20) {
            analysis.append("  - 故障树较复杂，建议检查是否可以模块化\n");
        } else {
            analysis.append("  - 故障树规模适中，结构良好\n");
        }
        
        if (depth > 5) {
            analysis.append("  - 故障树深度较大，建议优化层级结构\n");
        } else {
            analysis.append("  - 故障树深度合理\n");
        }
        
        analysis.append("\n2. 逻辑门检查：\n");
        checkLogicGates(faultTree, analysis);
        
        analysis.append("\n3. 完整性建议：\n");
        if (validationErrors != null && !validationErrors.isEmpty()) {
            analysis.append("  - 发现 ").append(validationErrors.size()).append(" 个问题需要修复\n");
            for (Map<String, Object> error : validationErrors) {
                analysis.append("  - ").append(error.get("errorType")).append(": ")
                      .append(error.get("message")).append("\n");
                if (error.get("suggestion") != null) {
                    analysis.append("    建议: ").append(error.get("suggestion")).append("\n");
                }
            }
        } else {
            analysis.append("  - 未发现结构性问题\n");
        }
        
        analysis.append("\n4. 优化建议：\n");
        analysis.append("  - 建议补充设备历史故障数据\n");
        analysis.append("  - 可考虑添加更多底事件的概率信息\n");
        analysis.append("  - 建议定期审查和更新故障树\n");
        
        analysis.append("\n注：此为系统生成的分析建议，重启Chat Service后可获取更智能的AI分析。");
        
        return analysis.toString();
    }

    private int countNodes(FaultTreeDTO node) {
        int count = 1;
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                count += countNodes(child);
            }
        }
        return count;
    }

    private int calculateDepth(FaultTreeDTO node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return 1;
        }
        int maxDepth = 0;
        for (FaultTreeDTO child : node.getChildren()) {
            maxDepth = Math.max(maxDepth, calculateDepth(child));
        }
        return maxDepth + 1;
    }

    private void checkLogicGates(FaultTreeDTO node, StringBuilder analysis) {
        if (node.getGateType() != null) {
            LogicGateEnum gateType = node.getGateType();
            int childCount = node.getChildren() != null ? node.getChildren().size() : 0;
            
            if (LogicGateEnum.AND.equals(gateType) && childCount < 2) {
                analysis.append("  - 警告: AND门应有至少2个子节点\n");
            } else if (LogicGateEnum.OR.equals(gateType) && childCount < 2) {
                analysis.append("  - 警告: OR门应有至少2个子节点\n");
            } else if (LogicGateEnum.XOR.equals(gateType) && childCount != 2) {
                analysis.append("  - 警告: XOR门应有恰好2个子节点\n");
            }
        }
        
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                checkLogicGates(child, analysis);
            }
        }
    }

    private String buildAnalysisPrompt(FaultTreeDTO faultTree, List<Map<String, Object>> validationErrors) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请分析以下故障树并给出专业建议：\n\n");

        prompt.append("【故障树基本信息】\n");
        if (faultTree.getName() != null) {
            prompt.append("- 故障树名称: ").append(faultTree.getName()).append("\n");
        }
        if (faultTree.getEquipmentType() != null) {
            prompt.append("- 设备类型: ").append(faultTree.getEquipmentType()).append("\n");
        }
        if (faultTree.getDescription() != null) {
            prompt.append("- 描述: ").append(faultTree.getDescription()).append("\n");
        }

        prompt.append("\n【故障树结构】\n");
        prompt.append(treeStructureToString(faultTree, 0));

        if (validationErrors != null && !validationErrors.isEmpty()) {
            prompt.append("\n【验证发现的问题】\n");
            for (Map<String, Object> error : validationErrors) {
                prompt.append("- ").append(error.get("errorType")).append(": ")
                      .append(error.get("message")).append("\n");
            }
        }

        prompt.append("\n请根据上述信息，从以下几个方面给出分析和建议：\n");
        prompt.append("1. 故障树结构的整体评价\n");
        prompt.append("2. 如果存在验证问题，请给出具体的修复建议\n");
        prompt.append("3. 逻辑门使用的合理性分析\n");
        prompt.append("4. 故障树完整性和覆盖度的建议\n");
        prompt.append("5. 其他专业优化建议\n");

        return prompt.toString();
    }

    private String treeStructureToString(FaultTreeDTO node, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);

        sb.append(indent);
        if (node.getGateType() != null) {
            sb.append("[").append(node.getGateType().name()).append("] ");
        }
        sb.append(node.getEventName() != null ? node.getEventName() : node.getEventId());
        sb.append(" (").append(node.getEventType() != null ? node.getEventType().name() : "unknown").append(")\n");

        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                sb.append(treeStructureToString(child, depth + 1));
            }
        }

        return sb.toString();
    }

    private String parseAIResponse(String responseBody) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            Object suggestion = responseMap.get("suggestion");
            if (suggestion != null) {
                return suggestion.toString();
            }
            Object result = responseMap.get("result");
            if (result != null) {
                return result.toString();
            }
            return responseBody;
        } catch (Exception e) {
            log.warn("Failed to parse AI response, returning raw: {}", e.getMessage());
            return responseBody;
        }
    }
}

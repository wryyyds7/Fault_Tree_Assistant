package com.cxyaqcdm.fta.feedback.service.impl;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.feedback.entity.FeedbackEntity;
import com.cxyaqcdm.fta.feedback.mapper.FeedbackMapper;
import com.cxyaqcdm.fta.feedback.service.FeedbackLearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackLearningServiceImpl implements FeedbackLearningService {

    private final FeedbackMapper feedbackMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${feedback.learning.batch.size}")
    private int batchSize;

    @Override
    public FeedbackEntity createFeedback(FeedbackEntity feedback) {
        try {
            // 生成唯一的feedbackId
            String feedbackId = "feedback_" + UUID.randomUUID().toString().replace("-", "");
            feedback.setFeedbackId(feedbackId);
            feedback.setCreatedAt();
            
            // 保存到数据库
            feedbackMapper.insert(feedback);
            
            // 发送消息到消息队列，通知其他服务有新的反馈
            rabbitTemplate.convertAndSend(
                AmqpConstants.EXCHANGE_FEEDBACK,
                AmqpConstants.ROUTING_KEY_FEEDBACK_CREATED,
                feedbackId
            );
            
            log.info("Created feedback: {}", feedbackId);
            return feedback;
        } catch (Exception e) {
            log.error("Error creating feedback: {}", e.getMessage());
            throw new RuntimeException("Failed to create feedback", e);
        }
    }

    @Override
    public FeedbackEntity getFeedback(String feedbackId) {
        return feedbackMapper.findById(Long.valueOf(feedbackId));
    }

    @Override
    public List<FeedbackEntity> getFeedbackByTreeId(String treeId) {
        return feedbackMapper.findByTreeId(treeId);
    }

    @Override
    public List<FeedbackEntity> getFeedbackByUserId(String userId) {
        return feedbackMapper.findByUserId(userId);
    }

    @Override
    public List<FeedbackEntity> getAllFeedback() {
        return feedbackMapper.findAll();
    }

    @Override
    public void processFeedbackBatch() {
        try {
            // 获取最近的未处理反馈批次
            List<FeedbackEntity> feedbackList = feedbackMapper.findTopNByOrderByCreatedAtDesc(batchSize);
            
            if (feedbackList.isEmpty()) {
                log.info("No feedback to process");
                return;
            }
            
            log.info("Processing {} feedback entries", feedbackList.size());
            
            // 分析反馈数据
            double avgAccuracy = feedbackList.stream()
                    .mapToDouble(FeedbackEntity::getAccuracyScore)
                    .average()
                    .orElse(0.0);
            
            double avgCompleteness = feedbackList.stream()
                    .mapToDouble(FeedbackEntity::getCompletenessScore)
                    .average()
                    .orElse(0.0);
            
            double avgClarity = feedbackList.stream()
                    .mapToDouble(FeedbackEntity::getClarityScore)
                    .average()
                    .orElse(0.0);
            
            // 分析反馈模式
            Map<String, Object> feedbackPatterns = analyzeFeedbackPatterns(feedbackList);
            
            log.info("Feedback analysis: accuracy={}, completeness={}, clarity={}",
                    avgAccuracy, avgCompleteness, avgClarity);
            
            // 发送分析结果到消息队列
            Map<String, Object> analysisResult = new HashMap<>();
            analysisResult.put("avgAccuracy", avgAccuracy);
            analysisResult.put("avgCompleteness", avgCompleteness);
            analysisResult.put("avgClarity", avgClarity);
            analysisResult.put("patterns", feedbackPatterns);
            
            rabbitTemplate.convertAndSend(
                AmqpConstants.EXCHANGE_FEEDBACK,
                AmqpConstants.ROUTING_KEY_FEEDBACK_ANALYZED,
                analysisResult
            );
        } catch (Exception e) {
            log.error("Error processing feedback batch: {}", e.getMessage());
        }
    }

    @Override
    public void optimizeModels() {
        try {
            log.info("Optimizing models based on feedback");
            
            // 获取所有反馈数据用于分析
            List<FeedbackEntity> allFeedback = feedbackMapper.findAll();
            
            if (allFeedback.isEmpty()) {
                log.info("No feedback available for model optimization");
                return;
            }
            
            // 分析反馈数据，提取模式
            Map<String, Object> patterns = analyzeFeedbackPatterns(allFeedback);
            
            // 生成优化建议
            Map<String, Object> optimizationSuggestions = generateOptimizationSuggestions(patterns);
            
            // 发送优化建议到知识图谱服务
            rabbitTemplate.convertAndSend(
                AmqpConstants.EXCHANGE_KNOWLEDGE_GRAPH,
                AmqpConstants.ROUTING_KEY_KNOWLEDGE_OPTIMIZE,
                optimizationSuggestions
            );
            
            // 发送优化建议到RAG服务
            rabbitTemplate.convertAndSend(
                AmqpConstants.EXCHANGE_RAG,
                AmqpConstants.ROUTING_KEY_RAG_OPTIMIZE,
                optimizationSuggestions
            );
            
            // 发送模型优化完成的消息
            rabbitTemplate.convertAndSend(
                AmqpConstants.EXCHANGE_FEEDBACK,
                AmqpConstants.ROUTING_KEY_MODELS_OPTIMIZED,
                optimizationSuggestions
            );
            
            log.info("Models optimized successfully with suggestions: {}", optimizationSuggestions.keySet());
        } catch (Exception e) {
            log.error("Error optimizing models: {}", e.getMessage());
        }
    }
    
    private Map<String, Object> analyzeFeedbackPatterns(List<FeedbackEntity> feedbackList) {
        Map<String, Object> patterns = new HashMap<>();
        
        // 分析低评分反馈
        List<FeedbackEntity> lowRatingFeedback = feedbackList.stream()
                .filter(f -> f.getRating() != null && f.getRating() < 3)
                .collect(Collectors.toList());
        
        // 分析常见问题
        Map<String, Integer> issueCounts = new HashMap<>();
        for (FeedbackEntity feedback : lowRatingFeedback) {
            if (feedback.getComments() != null) {
                String[] issues = feedback.getComments().toLowerCase().split("\\s+");
                for (String issue : issues) {
                    issueCounts.put(issue, issueCounts.getOrDefault(issue, 0) + 1);
                }
            }
        }
        
        // 分析改进建议
        List<String> suggestedChanges = feedbackList.stream()
                .map(FeedbackEntity::getSuggestedChanges)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        patterns.put("lowRatingCount", lowRatingFeedback.size());
        patterns.put("commonIssues", issueCounts);
        patterns.put("suggestedChanges", suggestedChanges);
        
        return patterns;
    }
    
    private Map<String, Object> generateOptimizationSuggestions(Map<String, Object> patterns) {
        Map<String, Object> suggestions = new HashMap<>();
        
        // 基于常见问题生成知识图谱优化建议
        Map<String, Integer> commonIssues = (Map<String, Integer>) patterns.get("commonIssues");
        if (commonIssues != null && !commonIssues.isEmpty()) {
            List<String> highPriorityIssues = commonIssues.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            suggestions.put("knowledgeGraphOptimizations", highPriorityIssues);
        }
        
        // 基于改进建议生成RAG优化建议
        List<String> suggestedChanges = (List<String>) patterns.get("suggestedChanges");
        if (suggestedChanges != null && !suggestedChanges.isEmpty()) {
            suggestions.put("ragOptimizations", suggestedChanges);
        }
        
        // 生成提示词优化建议
        suggestions.put("promptOptimizations", generatePromptOptimizations(patterns));
        
        return suggestions;
    }
    
    private List<String> generatePromptOptimizations(Map<String, Object> patterns) {
        List<String> optimizations = new ArrayList<>();
        
        // 基于反馈模式生成提示词优化建议
        Map<String, Integer> commonIssues = (Map<String, Integer>) patterns.get("commonIssues");
        if (commonIssues != null) {
            if (commonIssues.containsKey("accuracy")) {
                optimizations.add("Improve accuracy by including more specific technical details");
            }
            if (commonIssues.containsKey("completeness")) {
                optimizations.add("Ensure all relevant failure modes are included");
            }
            if (commonIssues.containsKey("clarity")) {
                optimizations.add("Use more concise and clear language");
            }
        }
        
        return optimizations;
    }
}

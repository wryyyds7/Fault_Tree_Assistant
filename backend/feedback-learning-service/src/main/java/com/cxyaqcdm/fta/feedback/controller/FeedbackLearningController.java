package com.cxyaqcdm.fta.feedback.controller;

import com.cxyaqcdm.fta.feedback.entity.FeedbackEntity;
import com.cxyaqcdm.fta.feedback.service.FeedbackLearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackLearningController {

    private final FeedbackLearningService feedbackLearningService;

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        throw new RuntimeException("User not authenticated");
    }

    @PostMapping
    public ResponseEntity<FeedbackEntity> createFeedback(@RequestBody FeedbackEntity feedback) {
        if (feedback.getUserId() == null) {
            feedback.setUserId(getCurrentUserId());
        }
        var savedFeedback = feedbackLearningService.createFeedback(feedback);
        return ResponseEntity.ok(savedFeedback);
    }

    @GetMapping("/{feedbackId}")
    public ResponseEntity<FeedbackEntity> getFeedback(@PathVariable String feedbackId) {
        var feedback = feedbackLearningService.getFeedback(feedbackId);
        if (feedback == null) {
            throw new RuntimeException("Feedback not found");
        }
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/tree/{treeId}")
    public ResponseEntity<List<FeedbackEntity>> getFeedbackByTreeId(@PathVariable String treeId) {
        var feedbackList = feedbackLearningService.getFeedbackByTreeId(treeId);
        return ResponseEntity.ok(feedbackList);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FeedbackEntity>> getFeedbackByUserId(@PathVariable String userId) {
        var feedbackList = feedbackLearningService.getFeedbackByUserId(userId);
        return ResponseEntity.ok(feedbackList);
    }

    @GetMapping
    public ResponseEntity<List<FeedbackEntity>> getAllFeedback() {
        var feedbackList = feedbackLearningService.getAllFeedback();
        return ResponseEntity.ok(feedbackList);
    }

    @PostMapping("/process-batch")
    public ResponseEntity<Void> processFeedbackBatch() {
        feedbackLearningService.processFeedbackBatch();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/optimize-models")
    public ResponseEntity<Void> optimizeModels() {
        feedbackLearningService.optimizeModels();
        return ResponseEntity.noContent().build();
    }
}

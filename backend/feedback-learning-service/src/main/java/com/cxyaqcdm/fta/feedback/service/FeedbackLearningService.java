package com.cxyaqcdm.fta.feedback.service;

import com.cxyaqcdm.fta.feedback.entity.FeedbackEntity;
import java.util.List;

public interface FeedbackLearningService {
    FeedbackEntity createFeedback(FeedbackEntity feedback);
    FeedbackEntity getFeedback(String feedbackId);
    List<FeedbackEntity> getFeedbackByTreeId(String treeId);
    List<FeedbackEntity> getFeedbackByUserId(String userId);
    List<FeedbackEntity> getAllFeedback();
    void processFeedbackBatch();
    void optimizeModels();
}

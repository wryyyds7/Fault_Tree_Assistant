package com.cxyaqcdm.fta.feedback.mapper;

import com.cxyaqcdm.fta.feedback.entity.FeedbackEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper {
    FeedbackEntity findById(@Param("id") Long id);
    FeedbackEntity findByFeedbackId(@Param("feedbackId") String feedbackId);
    List<FeedbackEntity> findByTreeId(@Param("treeId") String treeId);
    List<FeedbackEntity> findByUserId(@Param("userId") String userId);
    List<FeedbackEntity> findByFeedbackType(@Param("feedbackType") String feedbackType);
    List<FeedbackEntity> findByStatus(@Param("status") String status);
    List<FeedbackEntity> findAll();
    List<FeedbackEntity> findTopNByOrderByCreatedAtDesc(@Param("limit") int limit);
    void insert(FeedbackEntity feedback);
    void update(FeedbackEntity feedback);
    void updateStatus(@Param("feedbackId") String feedbackId, @Param("status") String status, @Param("processedBy") String processedBy, @Param("processedAt") java.time.LocalDateTime processedAt);
    void delete(@Param("feedbackId") String feedbackId);
    void deleteByTreeId(@Param("treeId") String treeId);
}
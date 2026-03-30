package com.cxyaqcdm.fta.feedback.mapper;

import com.cxyaqcdm.fta.feedback.entity.FeedbackEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper {
    FeedbackEntity findById(@Param("id") String id);
    List<FeedbackEntity> findByTreeId(@Param("treeId") String treeId);
    List<FeedbackEntity> findByUserId(@Param("userId") String userId);
    List<FeedbackEntity> findAll();
    List<FeedbackEntity> findTopNByOrderByCreatedAtDesc(@Param("limit") int limit);
    void insert(FeedbackEntity feedback);
}
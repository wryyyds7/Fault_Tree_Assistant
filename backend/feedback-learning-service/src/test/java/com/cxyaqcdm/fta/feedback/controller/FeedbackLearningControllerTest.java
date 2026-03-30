package com.cxyaqcdm.fta.feedback.controller;

import com.cxyaqcdm.fta.feedback.entity.FeedbackEntity;
import com.cxyaqcdm.fta.feedback.service.FeedbackLearningService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
public class FeedbackLearningControllerTest {

    @Mock
    private FeedbackLearningService feedbackLearningService;

    @InjectMocks
    private FeedbackLearningController feedbackLearningController;

    @Test
    public void testCreateFeedback() {
        // Arrange
        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setTreeId("1");
        feedback.setContent("Good fault tree");
        feedback.setUserId("admin");

        FeedbackEntity savedFeedback = new FeedbackEntity();
        savedFeedback.setId("1");
        savedFeedback.setTreeId("1");
        savedFeedback.setContent("Good fault tree");
        savedFeedback.setUserId("admin");

        when(feedbackLearningService.createFeedback(feedback)).thenReturn(savedFeedback);

        // Act
        ResponseEntity<FeedbackEntity> response = feedbackLearningController.createFeedback(feedback);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("1", response.getBody().getId());
        assertEquals("1", response.getBody().getTreeId());
        assertEquals("Good fault tree", response.getBody().getContent());
    }

    @Test
    public void testGetFeedback() {
        // Arrange
        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setId("1");
        feedback.setTreeId("1");
        feedback.setContent("Good fault tree");

        when(feedbackLearningService.getFeedback("1")).thenReturn(feedback);

        // Act
        ResponseEntity<FeedbackEntity> response = feedbackLearningController.getFeedback("1");

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("1", response.getBody().getId());
        assertEquals("1", response.getBody().getTreeId());
        assertEquals("Good fault tree", response.getBody().getContent());
    }

    @Test
    public void testGetFeedbackByTreeId() {
        // Arrange
        List<FeedbackEntity> feedbackList = new ArrayList<>();
        FeedbackEntity feedback1 = new FeedbackEntity();
        feedback1.setId("1");
        feedback1.setTreeId("1");
        feedback1.setContent("Good fault tree");
        feedbackList.add(feedback1);

        FeedbackEntity feedback2 = new FeedbackEntity();
        feedback2.setId("2");
        feedback2.setTreeId("1");
        feedback2.setContent("Needs improvement");
        feedbackList.add(feedback2);

        when(feedbackLearningService.getFeedbackByTreeId("1")).thenReturn(feedbackList);

        // Act
        ResponseEntity<List<FeedbackEntity>> response = feedbackLearningController.getFeedbackByTreeId("1");

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        assertEquals("1", response.getBody().get(0).getId());
        assertEquals("2", response.getBody().get(1).getId());
    }

    @Test
    public void testGetFeedbackByUserId() {
        // Arrange
        List<FeedbackEntity> feedbackList = new ArrayList<>();
        FeedbackEntity feedback1 = new FeedbackEntity();
        feedback1.setId("1");
        feedback1.setUserId("admin");
        feedback1.setContent("Good fault tree");
        feedbackList.add(feedback1);

        FeedbackEntity feedback2 = new FeedbackEntity();
        feedback2.setId("2");
        feedback2.setUserId("admin");
        feedback2.setContent("Needs improvement");
        feedbackList.add(feedback2);

        when(feedbackLearningService.getFeedbackByUserId("admin")).thenReturn(feedbackList);

        // Act
        ResponseEntity<List<FeedbackEntity>> response = feedbackLearningController.getFeedbackByUserId("admin");

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        assertEquals("1", response.getBody().get(0).getId());
        assertEquals("2", response.getBody().get(1).getId());
    }

    @Test
    public void testGetAllFeedback() {
        // Arrange
        List<FeedbackEntity> feedbackList = new ArrayList<>();
        FeedbackEntity feedback1 = new FeedbackEntity();
        feedback1.setId("1");
        feedback1.setContent("Good fault tree");
        feedbackList.add(feedback1);

        FeedbackEntity feedback2 = new FeedbackEntity();
        feedback2.setId("2");
        feedback2.setContent("Needs improvement");
        feedbackList.add(feedback2);

        when(feedbackLearningService.getAllFeedback()).thenReturn(feedbackList);

        // Act
        ResponseEntity<List<FeedbackEntity>> response = feedbackLearningController.getAllFeedback();

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        assertEquals("1", response.getBody().get(0).getId());
        assertEquals("2", response.getBody().get(1).getId());
    }

    @Test
    public void testProcessFeedbackBatch() {
        // Arrange
        doNothing().when(feedbackLearningService).processFeedbackBatch();

        // Act
        ResponseEntity<Void> response = feedbackLearningController.processFeedbackBatch();

        // Assert
        assertEquals(204, response.getStatusCodeValue());
    }

    @Test
    public void testOptimizeModels() {
        // Arrange
        doNothing().when(feedbackLearningService).optimizeModels();

        // Act
        ResponseEntity<Void> response = feedbackLearningController.optimizeModels();

        // Assert
        assertEquals(204, response.getStatusCodeValue());
    }
}

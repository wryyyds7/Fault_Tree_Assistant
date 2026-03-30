package com.cxyaqcdm.fta.knowledge.controller;

import com.cxyaqcdm.fta.knowledge.service.KnowledgeGraphService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
public class KnowledgeGraphControllerTest {

    @Mock
    private KnowledgeGraphService knowledgeGraphService;

    @InjectMocks
    private KnowledgeGraphController knowledgeGraphController;

    @Test
    public void testQueryTemplate() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("topEvent", "Machine Failure");
        request.put("equipmentType", "Pump");

        Map<String, Object> template = new HashMap<>();
        template.put("templateId", "1");
        template.put("topEvent", "Machine Failure");
        template.put("equipmentType", "Pump");
        template.put("nodes", new HashMap<>());
        template.put("edges", new HashMap<>());

        when(knowledgeGraphService.queryTemplate("Machine Failure", "Pump")).thenReturn(template);

        // Act
        ResponseEntity<Map<String, Object>> response = knowledgeGraphController.queryTemplate(request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("1", response.getBody().get("templateId"));
        assertEquals("Machine Failure", response.getBody().get("topEvent"));
        assertEquals("Pump", response.getBody().get("equipmentType"));
    }

    @Test
    public void testEnrichKnowledge() {
        // Arrange
        Map<String, Object> causalPattern = new HashMap<>();
        causalPattern.put("cause", "Low Oil Level");
        causalPattern.put("effect", "Bearing Failure");
        causalPattern.put("confidence", 0.9);

        doNothing().when(knowledgeGraphService).enrichKnowledge(causalPattern);

        // Act
        ResponseEntity<Void> response = knowledgeGraphController.enrichKnowledge(causalPattern);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    public void testInitializeOntology() {
        // Arrange
        doNothing().when(knowledgeGraphService).initializeOntology();

        // Act
        ResponseEntity<Void> response = knowledgeGraphController.initializeOntology();

        // Assert
        assertEquals(200, response.getStatusCodeValue());
    }
}

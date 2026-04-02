package com.cxyaqcdm.fta.document.controller;

import com.cxyaqcdm.fta.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
public class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private DocumentController documentController;

    @Test
    public void testUploadDocument() {
        // Arrange
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", "1");
        result.put("status", "success");
        result.put("message", "Document uploaded successfully");

        when(documentService.uploadDocument(multipartFile, "unknown", null, false)).thenReturn(result);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.uploadDocument(multipartFile, "unknown", null, false);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("1", response.getBody().get("documentId"));
        assertEquals("success", response.getBody().get("status"));
        assertEquals("Document uploaded successfully", response.getBody().get("message"));
    }
}

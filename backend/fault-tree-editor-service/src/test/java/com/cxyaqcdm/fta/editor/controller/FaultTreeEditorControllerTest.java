package com.cxyaqcdm.fta.editor.controller;

import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import com.cxyaqcdm.fta.editor.service.FaultTreeEditorService;
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
public class FaultTreeEditorControllerTest {

    @Mock
    private FaultTreeEditorService faultTreeEditorService;

    @InjectMocks
    private FaultTreeEditorController faultTreeEditorController;

    @Test
    public void testCreateFaultTree() {
        // Arrange
        FaultTreeDTO faultTreeDTO = new FaultTreeDTO();
        faultTreeDTO.setName("Test Fault Tree");

        FaultTreeEntity faultTreeEntity = new FaultTreeEntity();
        faultTreeEntity.setId("1");
        faultTreeEntity.setName("Test Fault Tree");
        faultTreeEntity.setUserId("admin");

        when(faultTreeEditorService.createFaultTree(faultTreeDTO, "admin")).thenReturn(faultTreeEntity);
        when(faultTreeEditorService.convertToDTO(faultTreeEntity)).thenReturn(faultTreeDTO);

        // Act
        ResponseEntity<FaultTreeDTO> response = faultTreeEditorController.createFaultTree(faultTreeDTO);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Test Fault Tree", response.getBody().getName());
    }

    @Test
    public void testGetFaultTree() {
        // Arrange
        FaultTreeEntity faultTreeEntity = new FaultTreeEntity();
        faultTreeEntity.setId("1");
        faultTreeEntity.setName("Test Fault Tree");

        FaultTreeDTO faultTreeDTO = new FaultTreeDTO();
        faultTreeDTO.setId("1");
        faultTreeDTO.setName("Test Fault Tree");

        when(faultTreeEditorService.getFaultTree("1")).thenReturn(faultTreeEntity);
        when(faultTreeEditorService.convertToDTO(faultTreeEntity)).thenReturn(faultTreeDTO);

        // Act
        ResponseEntity<FaultTreeDTO> response = faultTreeEditorController.getFaultTree("1");

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("1", response.getBody().getId());
        assertEquals("Test Fault Tree", response.getBody().getName());
    }

    @Test
    public void testGetAllFaultTrees() {
        // Arrange
        List<FaultTreeEntity> faultTreeEntities = new ArrayList<>();
        FaultTreeEntity entity1 = new FaultTreeEntity();
        entity1.setId("1");
        entity1.setName("Fault Tree 1");
        faultTreeEntities.add(entity1);

        FaultTreeEntity entity2 = new FaultTreeEntity();
        entity2.setId("2");
        entity2.setName("Fault Tree 2");
        faultTreeEntities.add(entity2);

        List<FaultTreeDTO> faultTreeDTOs = new ArrayList<>();
        FaultTreeDTO dto1 = new FaultTreeDTO();
        dto1.setId("1");
        dto1.setName("Fault Tree 1");
        faultTreeDTOs.add(dto1);

        FaultTreeDTO dto2 = new FaultTreeDTO();
        dto2.setId("2");
        dto2.setName("Fault Tree 2");
        faultTreeDTOs.add(dto2);

        when(faultTreeEditorService.getAllFaultTrees()).thenReturn(faultTreeEntities);
        when(faultTreeEditorService.convertToDTO(entity1)).thenReturn(dto1);
        when(faultTreeEditorService.convertToDTO(entity2)).thenReturn(dto2);

        // Act
        ResponseEntity<List<FaultTreeDTO>> response = faultTreeEditorController.getAllFaultTrees();

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        assertEquals("1", response.getBody().get(0).getId());
        assertEquals("2", response.getBody().get(1).getId());
    }

    @Test
    public void testUpdateFaultTree() {
        // Arrange
        FaultTreeDTO faultTreeDTO = new FaultTreeDTO();
        faultTreeDTO.setName("Updated Fault Tree");

        FaultTreeEntity faultTreeEntity = new FaultTreeEntity();
        faultTreeEntity.setId("1");
        faultTreeEntity.setName("Updated Fault Tree");

        when(faultTreeEditorService.updateFaultTree("1", faultTreeDTO, "admin")).thenReturn(faultTreeEntity);
        when(faultTreeEditorService.convertToDTO(faultTreeEntity)).thenReturn(faultTreeDTO);

        // Act
        ResponseEntity<FaultTreeDTO> response = faultTreeEditorController.updateFaultTree("1", faultTreeDTO);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Updated Fault Tree", response.getBody().getName());
    }

    @Test
    public void testDeleteFaultTree() {
        // Arrange
        doNothing().when(faultTreeEditorService).deleteFaultTree("1");

        // Act
        ResponseEntity<Void> response = faultTreeEditorController.deleteFaultTree("1");

        // Assert
        assertEquals(204, response.getStatusCodeValue());
    }
}

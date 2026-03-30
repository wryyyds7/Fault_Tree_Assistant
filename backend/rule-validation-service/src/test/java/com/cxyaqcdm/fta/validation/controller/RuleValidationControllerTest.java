package com.cxyaqcdm.fta.validation.controller;

import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.validation.dto.ValidationResultDTO;
import com.cxyaqcdm.fta.validation.service.RuleValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
public class RuleValidationControllerTest {

    @Mock
    private RuleValidationService ruleValidationService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RuleValidationController ruleValidationController;

    @Test
    public void testValidateFaultTree() {
        // Arrange
        FaultTreeDTO faultTreeDTO = new FaultTreeDTO();
        faultTreeDTO.setId("1");
        faultTreeDTO.setName("Test Fault Tree");

        ValidationResultDTO validationResultDTO = new ValidationResultDTO();
        validationResultDTO.setValid(true);
        validationResultDTO.setMessage("Validation passed");

        when(ruleValidationService.validateFaultTree(faultTreeDTO)).thenReturn(validationResultDTO);

        // Act
        ResponseEntity<ValidationResultDTO> response = ruleValidationController.validateFaultTree(faultTreeDTO);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(true, response.getBody().isValid());
        assertEquals("Validation passed", response.getBody().getMessage());
    }
}

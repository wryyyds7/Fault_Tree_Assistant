package com.cxyaqcdm.fta.validation.controller;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.validation.dto.ValidationResultDTO;
import com.cxyaqcdm.fta.validation.service.RuleValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/validation")
@RequiredArgsConstructor
@Slf4j
public class RuleValidationController {

    private final RuleValidationService ruleValidationService;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping("/validate")
    public ResponseEntity<ValidationResultDTO> validateFaultTree(@RequestBody FaultTreeDTO faultTree) {
        ValidationResultDTO result = ruleValidationService.validateFaultTree(faultTree);
        return ResponseEntity.ok(result);
    }

    @RabbitListener(queues = AmqpConstants.QUEUE_VALIDATE_FAULT_TREE)
    public void handleValidationRequest(Map<String, Object> message) {
        try {
            // 解析消息
            String taskId = (String) message.get("taskId");
            FaultTreeDTO faultTree = (FaultTreeDTO) message.get("faultTree");
            
            log.info("Received validation request for task: {}", taskId);
            
            // 执行校验
            ValidationResultDTO result = ruleValidationService.validateFaultTree(faultTree);
            
            // 发送校验结果
            Map<String, Object> response = Map.of(
                "taskId", taskId,
                "result", result
            );
            
            rabbitTemplate.convertAndSend(
                AmqpConstants.EXCHANGE_VALIDATION, 
                AmqpConstants.ROUTING_KEY_VALIDATION_RESULT, 
                response
            );
            
            log.info("Validation completed for task: {}", taskId);
        } catch (Exception e) {
            log.error("Error handling validation request: {}", e.getMessage());
        }
    }
}

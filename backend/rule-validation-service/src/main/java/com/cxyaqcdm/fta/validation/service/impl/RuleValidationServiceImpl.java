package com.cxyaqcdm.fta.validation.service.impl;

import com.cxyaqcdm.fta.common.constants.ValidationRuleCode;
import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.common.enums.EventTypeEnum;
import com.cxyaqcdm.fta.common.enums.LogicGateEnum;
import com.cxyaqcdm.fta.validation.dto.ValidationResultDTO;
import com.cxyaqcdm.fta.validation.service.RuleValidationService;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class RuleValidationServiceImpl implements RuleValidationService {

    private KieContainer kieContainer;

    @Value("${rule.engine.rules.path}")
    private String rulesPath;

    @PostConstruct
    public void init() {
        try {
            // 初始化Drools规则引擎
            KieServices kieServices = KieServices.Factory.get();
            KieRepository kieRepository = kieServices.getRepository();
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            
            // 加载规则文件
            File rulesDir = new File(rulesPath);
            if (rulesDir.exists() && rulesDir.isDirectory()) {
                File[] ruleFiles = rulesDir.listFiles((dir, name) -> name.endsWith(".drl"));
                if (ruleFiles != null) {
                    for (File ruleFile : ruleFiles) {
                        kieFileSystem.write("src/main/resources/" + ruleFile.getName(),
                                kieServices.getResources().newFileSystemResource(ruleFile));
                    }
                }
            }
            
            // 构建规则
            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();
            KieModule kieModule = kieBuilder.getKieModule();
            kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
            
            log.info("Drools rule engine initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Drools rule engine: {}", e.getMessage());
        }
    }

    @Override
    public ValidationResultDTO validateFaultTree(FaultTreeDTO faultTree) {
        ValidationResultDTO result = new ValidationResultDTO();
        List<ValidationResultDTO.ValidationErrorDTO> errors = new ArrayList<>();
        
        // 检查循环依赖
        checkCyclicDependency(faultTree, new HashSet<>(), errors);
        
        // 检查节点类型合法性
        checkNodeTypeValidity(faultTree, errors);
        
        // 检查逻辑门连接数
        checkGateConnectionCount(faultTree, errors);
        
        // 检查本体一致性
        checkOntologyConsistency(faultTree, errors);
        
        // 设置结果
        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        
        return result;
    }

    private void checkCyclicDependency(FaultTreeDTO node, Set<String> visited, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (visited.contains(node.getEventId())) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(ValidationRuleCode.CYCLE_DETECTED);
            error.setNodeId(node.getEventId());
            error.setMessage("Detected cyclic dependency involving node: " + node.getEventId());
            error.setErrorType("CIRCULAR_DEPENDENCY");
            error.setSuggestion("Remove the circular reference by restructuring the fault tree hierarchy. Break the cycle at node: " + node.getEventName());
            errors.add(error);
            return;
        }

        visited.add(node.getEventId());

        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                checkCyclicDependency(child, new HashSet<>(visited), errors);
            }
        }
    }

    private void checkNodeTypeValidity(FaultTreeDTO node, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (EventTypeEnum.BASIC == node.getEventType() &&
            node.getChildren() != null && !node.getChildren().isEmpty()) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(ValidationRuleCode.BASIC_EVENT_HAS_CHILDREN);
            error.setNodeId(node.getEventId());
            error.setMessage("Basic event cannot have children: " + node.getEventName());
            error.setErrorType("INVALID_BASIC_NODE");
            error.setSuggestion("Remove child nodes from basic event '" + node.getEventName() + "' or change its type to INTERMEDIATE if it should have children.");
            errors.add(error);
        }

        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                checkNodeTypeValidity(child, errors);
            }
        }
    }

    private void checkGateConnectionCount(FaultTreeDTO node, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (node.getGateType() != null) {
            LogicGateEnum gateType = node.getGateType();
            if ((gateType == LogicGateEnum.AND || gateType == LogicGateEnum.OR) &&
                (node.getChildren() == null || node.getChildren().size() < 2)) {
                ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
                error.setCode(ValidationRuleCode.INSUFFICIENT_INPUTS);
                error.setNodeId(node.getEventId());
                error.setMessage(gateType + " gate must have at least two inputs. Current: " +
                    (node.getChildren() == null ? 0 : node.getChildren().size()));
                error.setErrorType("INSUFFICIENT_INPUTS");
                error.setSuggestion("Add more child nodes to " + gateType + " gate '" + node.getEventName() +
                    "' to meet the minimum requirement of 2 inputs.");
                errors.add(error);
            }
        }

        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                checkGateConnectionCount(child, errors);
            }
        }
    }

    private void checkOntologyConsistency(FaultTreeDTO node, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (node.getEventType() == null) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(ValidationRuleCode.ONTOLOGY_INCONSISTENCY);
            error.setNodeId(node.getEventId());
            error.setMessage("Event type cannot be null for event: " + node.getEventId());
            error.setErrorType("MISSING_EVENT_TYPE");
            error.setSuggestion("Assign an event type (TOP, INTERMEDIATE, or BASIC) to event '" + node.getEventId() + "'.");
            errors.add(error);
        }

        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                checkOntologyConsistency(child, errors);
            }
        }
    }
}

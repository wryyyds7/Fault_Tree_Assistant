package com.cxyaqcdm.fta.validation.service.impl;

import com.cxyaqcdm.fta.common.constants.ValidationRuleCode;
import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.common.enums.EventTypeEnum;
import com.cxyaqcdm.fta.common.enums.LogicGateEnum;
import com.cxyaqcdm.fta.validation.client.AIAnalysisClient;
import com.cxyaqcdm.fta.validation.dto.ValidationResultDTO;
import com.cxyaqcdm.fta.validation.service.RuleValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message.Level;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class RuleValidationServiceImpl implements RuleValidationService {

    private KieContainer kieContainer;
    private boolean droolsInitialized = false;

    private final AIAnalysisClient aiAnalysisClient;
    private final ObjectMapper objectMapper;

    @Value("${rule.engine.rules.path:rules}")
    private String rulesPath;

    public RuleValidationServiceImpl(AIAnalysisClient aiAnalysisClient, ObjectMapper objectMapper) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieRepository kieRepository = kieServices.getRepository();
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            
            try {
                ClassPathResource resource = new ClassPathResource("rules/fault-tree-validation.drl");
                if (resource.exists()) {
                    kieFileSystem.write("src/main/resources/rules/fault-tree-validation.drl",
                            kieServices.getResources().newInputStreamResource(resource.getInputStream()));
                    log.info("Loaded Drools rules from classpath");
                }
            } catch (Exception e) {
                log.warn("Failed to load rules from classpath: {}", e.getMessage());
            }
            
            File rulesDir = new File(rulesPath);
            if (rulesDir.exists() && rulesDir.isDirectory()) {
                File[] ruleFiles = rulesDir.listFiles((dir, name) -> name.endsWith(".drl"));
                if (ruleFiles != null) {
                    for (File ruleFile : ruleFiles) {
                        kieFileSystem.write("src/main/resources/" + ruleFile.getName(),
                                kieServices.getResources().newFileSystemResource(ruleFile));
                    }
                    log.info("Loaded {} Drools rule files from directory", ruleFiles.length);
                }
            }
            
            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();
            
            if (kieBuilder.getResults().hasMessages(Level.ERROR)) {
                log.error("Drools rule compilation errors: {}", kieBuilder.getResults().getMessages());
            } else {
                KieModule kieModule = kieBuilder.getKieModule();
                kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
                droolsInitialized = true;
                log.info("Drools rule engine initialized successfully");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Drools rule engine: {}", e.getMessage(), e);
        }
    }

    @Override
    public ValidationResultDTO validateFaultTree(FaultTreeDTO faultTree) {
        ValidationResultDTO result = new ValidationResultDTO();
        List<ValidationResultDTO.ValidationErrorDTO> errors = new ArrayList<>();
        
        if (droolsInitialized && kieContainer != null) {
            try {
                log.info("Validating fault tree using Drools rules");
                errors = validateWithDrools(faultTree);
            } catch (Exception e) {
                log.error("Drools validation failed, falling back to manual validation: {}", e.getMessage());
                errors = validateManually(faultTree);
            }
        } else {
            log.warn("Drools not initialized, using manual validation");
            errors = validateManually(faultTree);
        }
        
        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        
        // 调用AI进行智能分析
        try {
            log.info("Requesting AI analysis for fault tree");
            List<Map<String, Object>> errorList = convertErrorsToMap(errors);
            String aiSuggestion = aiAnalysisClient.analyzeFaultTree(faultTree, errorList);
            
            if (aiSuggestion != null && !aiSuggestion.trim().isEmpty()) {
                result.setAiSuggestion(aiSuggestion);
                result.setAiAnalysisCompleted(true);
                log.info("AI analysis completed successfully");
            } else {
                result.setAiAnalysisCompleted(false);
                log.warn("AI analysis returned empty result");
            }
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage(), e);
            result.setAiAnalysisCompleted(false);
        }
        
        log.info("Fault tree validation complete. Valid: {}, Errors: {}, AI Analysis: {}", 
            result.isValid(), errors.size(), result.isAiAnalysisCompleted());
        return result;
    }

    private List<Map<String, Object>> convertErrorsToMap(List<ValidationResultDTO.ValidationErrorDTO> errors) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ValidationResultDTO.ValidationErrorDTO error : errors) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("code", error.getCode());
            errorMap.put("nodeId", error.getNodeId());
            errorMap.put("message", error.getMessage());
            errorMap.put("errorType", error.getErrorType());
            errorMap.put("suggestion", error.getSuggestion());
            result.add(errorMap);
        }
        return result;
    }
    
    private List<ValidationResultDTO.ValidationErrorDTO> validateWithDrools(FaultTreeDTO faultTree) {
        List<ValidationResultDTO.ValidationErrorDTO> errors = new ArrayList<>();
        KieSession kieSession = null;
        
        try {
            kieSession = kieContainer.newKieSession();
            kieSession.setGlobal("errors", errors);
            
            insertAllNodes(kieSession, faultTree);
            
            int firedRules = kieSession.fireAllRules();
            log.info("Fired {} Drools validation rules", firedRules);
            
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }
        
        return errors;
    }
    
    private void insertAllNodes(KieSession kieSession, FaultTreeDTO node) {
        kieSession.insert(node);
        
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                insertAllNodes(kieSession, child);
            }
        }
    }
    
    private List<ValidationResultDTO.ValidationErrorDTO> validateManually(FaultTreeDTO faultTree) {
        List<ValidationResultDTO.ValidationErrorDTO> errors = new ArrayList<>();
        
        checkCyclicDependency(faultTree, new HashSet<>(), errors);
        checkNodeTypeValidity(faultTree, errors);
        checkGateConnectionCount(faultTree, errors);
        checkOntologyConsistency(faultTree, errors);
        checkSingleTopEvent(faultTree, errors);
        checkEventNames(faultTree, new HashSet<>(), errors);
        
        return errors;
    }

    private void checkCyclicDependency(FaultTreeDTO node, Set<String> visited, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (visited.contains(node.getEventId())) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(String.valueOf(ValidationRuleCode.CYCLE_DETECTED));
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
            error.setCode(String.valueOf(ValidationRuleCode.BASIC_EVENT_HAS_CHILDREN));
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
                error.setCode(String.valueOf(ValidationRuleCode.INSUFFICIENT_INPUTS));
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
            error.setCode(String.valueOf(ValidationRuleCode.ONTOLOGY_INCONSISTENCY));
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
    
    private void checkSingleTopEvent(FaultTreeDTO root, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (root.getEventType() != EventTypeEnum.TOP) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(String.valueOf(ValidationRuleCode.MISSING_TOP_EVENT));
            error.setNodeId(root.getEventId());
            error.setMessage("Root node must be a TOP event");
            error.setErrorType("INVALID_TOP_EVENT");
            error.setSuggestion("Set the root node event type to TOP.");
            errors.add(error);
        }
        
        long topEventCount = countTopEvents(root, new HashSet<>());
        if (topEventCount > 1) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(String.valueOf(ValidationRuleCode.INVALID_EVENT_TYPE));
            error.setNodeId(root.getEventId());
            error.setMessage("Multiple TOP events found: " + topEventCount);
            error.setErrorType("MULTIPLE_TOPS");
            error.setSuggestion("Ensure only one TOP event exists at the root of the fault tree.");
            errors.add(error);
        }
    }
    
    private long countTopEvents(FaultTreeDTO node, Set<String> visited) {
        if (visited.contains(node.getEventId())) {
            return 0;
        }
        visited.add(node.getEventId());
        
        long count = (node.getEventType() == EventTypeEnum.TOP) ? 1 : 0;
        
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                count += countTopEvents(child, visited);
            }
        }
        
        return count;
    }
    
    private void checkEventNames(FaultTreeDTO node, Set<String> eventIds, List<ValidationResultDTO.ValidationErrorDTO> errors) {
        if (eventIds.contains(node.getEventId())) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(String.valueOf(ValidationRuleCode.INVALID_NODE_STRUCTURE));
            error.setNodeId(node.getEventId());
            error.setMessage("Duplicate event ID found: " + node.getEventId());
            error.setErrorType("DUPLICATE_ID");
            error.setSuggestion("Ensure each event has a unique eventId.");
            errors.add(error);
        } else {
            eventIds.add(node.getEventId());
        }
        
        if (node.getEventName() == null || node.getEventName().trim().isEmpty()) {
            ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
            error.setCode(String.valueOf(ValidationRuleCode.MISSING_REQUIRED_PROPERTY));
            error.setNodeId(node.getEventId());
            error.setMessage("Event name cannot be empty");
            error.setErrorType("EMPTY_NAME");
            error.setSuggestion("Provide a meaningful name for the event.");
            errors.add(error);
        }
        
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                checkEventNames(child, eventIds, errors);
            }
        }
    }
}

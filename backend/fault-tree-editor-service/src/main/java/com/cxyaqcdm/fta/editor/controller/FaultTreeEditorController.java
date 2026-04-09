package com.cxyaqcdm.fta.editor.controller;

import com.cxyaqcdm.fta.common.context.UserContext;
import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import com.cxyaqcdm.fta.editor.service.FaultTreeEditorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fault-trees")
@RequiredArgsConstructor
@Slf4j
public class FaultTreeEditorController {

    private final FaultTreeEditorService faultTreeEditorService;

    private String getCurrentUserId() {
        UserContext userContext = UserContext.getCurrentUser();
        if (userContext != null) {
            return userContext.getUserId();
        }
        return null;
    }

    private String getCurrentUserRole() {
        UserContext userContext = UserContext.getCurrentUser();
        if (userContext != null) {
            return userContext.getRole();
        }
        return "USER";
    }

    @PostMapping
    public ResponseEntity<FaultTreeDTO> createFaultTree(@RequestBody FaultTreeDTO faultTreeDTO) {
        String userId = getCurrentUserId();
        var entity = faultTreeEditorService.createFaultTree(faultTreeDTO, userId);
        var dto = faultTreeEditorService.convertToDTO(entity);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{treeId}")
    public ResponseEntity<FaultTreeDTO> getFaultTree(@PathVariable String treeId) {
        var entity = faultTreeEditorService.getFaultTree(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        var dto = faultTreeEditorService.convertToDTO(entity);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<FaultTreeDTO>> getAllFaultTrees() {
        String role = getCurrentUserRole();
        String currentUserId = getCurrentUserId();
        List<FaultTreeEntity> entities;

        if ("ADMIN".equals(role)) {
            entities = faultTreeEditorService.getAllFaultTrees();
        } else {
            entities = faultTreeEditorService.getFaultTreesByCreatedBy(currentUserId);
        }

        var dtos = entities.stream()
                .map(faultTreeEditorService::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{treeId}")
    public ResponseEntity<FaultTreeDTO> updateFaultTree(@PathVariable String treeId, @RequestBody FaultTreeDTO faultTreeDTO) {
        String userId = getCurrentUserId();
        var entity = faultTreeEditorService.updateFaultTree(treeId, faultTreeDTO, userId);
        var dto = faultTreeEditorService.convertToDTO(entity);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{treeId}")
    public ResponseEntity<Void> deleteFaultTree(@PathVariable String treeId) {
        faultTreeEditorService.deleteFaultTree(treeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{treeId}/nodes")
    public ResponseEntity<FaultTreeDTO> addNode(@PathVariable String treeId,
                                                 @RequestParam(required = false) String parentEventId,
                                                 @RequestBody FaultTreeDTO newNode) {
        String userId = getCurrentUserId();
        FaultTreeDTO parentNode = null;
        if (parentEventId != null) {
            var entity = faultTreeEditorService.getFaultTree(treeId);
            if (entity != null) {
                parentNode = faultTreeEditorService.convertToDTO(entity);
            }
        }
        var result = faultTreeEditorService.addNode(treeId, parentNode, newNode, userId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{treeId}/nodes")
    public ResponseEntity<FaultTreeDTO> updateNode(@PathVariable String treeId, @RequestBody FaultTreeDTO updatedNode) {
        String userId = getCurrentUserId();
        var result = faultTreeEditorService.updateNode(treeId, updatedNode, userId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{treeId}/nodes/{eventId}")
    public ResponseEntity<Void> deleteNode(@PathVariable String treeId, @PathVariable String eventId) {
        String userId = getCurrentUserId();
        faultTreeEditorService.deleteNode(treeId, eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{treeId}/nodes/{eventId}/move")
    public ResponseEntity<FaultTreeDTO> moveNode(@PathVariable String treeId,
                                                 @PathVariable String eventId,
                                                 @RequestParam(required = false) String newParentId) {
        String userId = getCurrentUserId();
        var result = faultTreeEditorService.moveNode(treeId, eventId, newParentId, userId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{treeId}/positions")
    public ResponseEntity<Void> updateNodePositions(@PathVariable String treeId,
                                                      @RequestBody Map<String, FaultTreeDTO.PositionDTO> positions) {
        String userId = getCurrentUserId();
        faultTreeEditorService.updateNodePositions(treeId, positions, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{treeId}/nodes/{eventId}/confidence")
    public ResponseEntity<FaultTreeDTO> updateNodeConfidence(@PathVariable String treeId,
                                                               @PathVariable String eventId,
                                                               @RequestBody Map<String, Object> confidenceData) {
        String userId = getCurrentUserId();
        Double confidence = confidenceData.get("confidence") != null ?
            ((Number) confidenceData.get("confidence")).doubleValue() : null;
        String verificationStatus = (String) confidenceData.get("verificationStatus");
        var result = faultTreeEditorService.updateNodeConfidence(treeId, eventId, confidence, verificationStatus, userId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{treeId}/nodes/{eventId}/verification")
    public ResponseEntity<FaultTreeDTO> updateNodeVerificationStatus(@PathVariable String treeId,
                                                                        @PathVariable String eventId,
                                                                        @RequestBody Map<String, String> statusData) {
        String userId = getCurrentUserId();
        var result = faultTreeEditorService.updateNodeVerificationStatus(treeId, eventId, statusData.get("status"), userId);
        return ResponseEntity.ok(result);
    }
}

package com.cxyaqcdm.fta.editor.controller;

import com.cxyaqcdm.fta.common.context.UserContext;
import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import com.cxyaqcdm.fta.editor.entity.FaultTreeVersionEntity;
import com.cxyaqcdm.fta.editor.service.FaultTreeEditorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fault-trees")
@RequiredArgsConstructor
@Slf4j
public class FaultTreeEditorController {

    private final FaultTreeEditorService faultTreeEditorService;
    private final ObjectMapper objectMapper;

    @Data
    public static class FaultTreeVersionDTO {
        private String versionId;
        private String treeId;
        private Integer versionNumber;
        private String changeSummary;
        private String changedBy;
        private LocalDateTime createdAt;
        private FaultTreeDTO treeData;
    }

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
        log.info("🔍 [POST] /api/v1/fault-trees - 创建故障树");
        log.info("📥 [Controller] 接收到的 FaultTreeDTO: {}", faultTreeDTO);
        log.info("📥 [Controller] faultTreeDTO.name = {}", faultTreeDTO.getName());
        log.info("📥 [Controller] faultTreeDTO.eventName = {}", faultTreeDTO.getEventName());
        log.info("📥 [Controller] faultTreeDTO.eventId = {}", faultTreeDTO.getEventId());
        log.info("📥 [Controller] faultTreeDTO.eventType = {}", faultTreeDTO.getEventType());
        log.info("📥 [Controller] faultTreeDTO.equipmentType = {}", faultTreeDTO.getEquipmentType());
        log.info("📥 [Controller] faultTreeDTO.description = {}", faultTreeDTO.getDescription());
        if (faultTreeDTO.getChildren() != null) {
            log.info("📥 [Controller] faultTreeDTO.children 数量 = {}", faultTreeDTO.getChildren().size());
        }
        String userId = getCurrentUserId();
        log.info("📥 [Controller] 当前用户ID: {}", userId);
        var entity = faultTreeEditorService.createFaultTree(faultTreeDTO, userId);
        var dto = faultTreeEditorService.convertToDTO(entity);
        log.info("✅ [Controller] 故障树创建成功, 返回的 DTO: treeId={}, name={}", dto.getTreeId(), dto.getName());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{treeId}")
    public ResponseEntity<FaultTreeDTO> getFaultTree(@PathVariable String treeId) {
        log.info("🔍 [GET] /api/v1/fault-trees/{} - 获取故障树详情", treeId);
        var entity = faultTreeEditorService.getFaultTree(treeId);
        if (entity == null) {
            log.warn("⚠️ 故障树未找到: {}", treeId);
            throw new RuntimeException("Fault tree not found");
        }
        var dto = faultTreeEditorService.convertToDTO(entity);
        log.info("✅ 返回故障树DTO: treeId={}, name={}", dto.getTreeId(), dto.getName());
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<FaultTreeDTO>> getAllFaultTrees() {
        String role = getCurrentUserRole();
        String currentUserId = getCurrentUserId();
        log.info("🔍 [GET] /api/v1/fault-trees - 获取故障树列表, role={}, userId={}", role, currentUserId);
        List<FaultTreeEntity> entities;

        if ("ADMIN".equals(role)) {
            entities = faultTreeEditorService.getAllFaultTrees();
        } else {
            entities = faultTreeEditorService.getFaultTreesByCreatedBy(currentUserId);
        }

        var dtos = entities.stream()
                .map(faultTreeEditorService::convertToDTO)
                .collect(Collectors.toList());
        log.info("✅ 返回故障树列表: 数量={}", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{treeId}")
    public ResponseEntity<FaultTreeDTO> updateFaultTree(@PathVariable String treeId, @RequestBody FaultTreeDTO faultTreeDTO) {
        log.info("🔍 [PUT] /api/v1/fault-trees/{} - 更新故障树", treeId);
        log.info("📥 [Controller] 接收到的 FaultTreeDTO: {}", faultTreeDTO);
        log.info("📥 [Controller] faultTreeDTO.name = {}", faultTreeDTO.getName());
        log.info("📥 [Controller] faultTreeDTO.eventName = {}", faultTreeDTO.getEventName());
        String userId = getCurrentUserId();
        var entity = faultTreeEditorService.updateFaultTree(treeId, faultTreeDTO, userId);
        var dto = faultTreeEditorService.convertToDTO(entity);
        log.info("✅ [Controller] 故障树更新成功");
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{treeId}")
    public ResponseEntity<Void> deleteFaultTree(@PathVariable String treeId) {
        faultTreeEditorService.deleteFaultTree(treeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{treeId}/versions")
    public ResponseEntity<List<FaultTreeVersionEntity>> getVersions(@PathVariable String treeId) {
        log.info("🔍 [GET] /api/v1/fault-trees/{}/versions - 获取版本列表", treeId);
        List<FaultTreeVersionEntity> versions = faultTreeEditorService.getVersions(treeId);
        return ResponseEntity.ok(versions);
    }

    @PostMapping("/{treeId}/versions")
    public ResponseEntity<FaultTreeVersionEntity> createVersion(@PathVariable String treeId,
                                                                 @RequestBody(required = false) Map<String, String> versionData) {
        log.info("🔍 [POST] /api/v1/fault-trees/{}/versions - 创建新版本", treeId);
        String userId = getCurrentUserId();
        String changeSummary = versionData != null ? versionData.get("changeSummary") : null;
        FaultTreeVersionEntity version = faultTreeEditorService.createVersion(treeId, changeSummary, userId);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/{treeId}/versions/{versionNumber}")
    public ResponseEntity<FaultTreeVersionDTO> getVersion(@PathVariable String treeId, @PathVariable Integer versionNumber) {
        log.info("🔍 [GET] /api/v1/fault-trees/{}/versions/{} - 获取指定版本", treeId, versionNumber);
        FaultTreeVersionEntity version = faultTreeEditorService.getVersion(treeId, versionNumber);
        if (version == null) {
            throw new RuntimeException("Version not found");
        }
        FaultTreeVersionDTO dto = new FaultTreeVersionDTO();
        dto.setVersionId(version.getVersionId());
        dto.setTreeId(version.getTreeId());
        dto.setVersionNumber(version.getVersionNumber());
        dto.setChangeSummary(version.getChangeSummary());
        dto.setChangedBy(version.getChangedBy());
        dto.setCreatedAt(version.getCreatedAt());
        try {
            dto.setTreeData(objectMapper.readValue(version.getTreeDataSnapshot(), FaultTreeDTO.class));
        } catch (Exception e) {
            log.error("Error parsing tree data snapshot", e);
        }
        return ResponseEntity.ok(dto);
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

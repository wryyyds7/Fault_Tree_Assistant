package com.cxyaqcdm.fta.editor.service.impl;

import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import com.cxyaqcdm.fta.editor.mapper.FaultTreeMapper;
import com.cxyaqcdm.fta.editor.service.FaultTreeEditorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaultTreeEditorServiceImpl implements FaultTreeEditorService {

    private final FaultTreeMapper faultTreeMapper;
    private final ObjectMapper objectMapper;

    @Override
    public FaultTreeEntity createFaultTree(FaultTreeDTO faultTreeDTO, String userId) {
        try {
            // 生成唯一的treeId
            String treeId = "tree_" + UUID.randomUUID().toString().replace("-", "");
            
            // 转换为实体
            FaultTreeEntity entity = convertToEntity(faultTreeDTO);
            entity.setTreeId(treeId);
            entity.setCreatedBy(userId);
            entity.setUpdatedBy(userId);
            entity.setCreatedAt();
            
            // 保存到数据库
            faultTreeMapper.insert(entity);
            return entity;
        } catch (Exception e) {
            log.error("Error creating fault tree: {}", e.getMessage());
            throw new RuntimeException("Failed to create fault tree", e);
        }
    }

    @Override
    public FaultTreeEntity getFaultTree(String treeId) {
        return faultTreeMapper.findByTreeId(treeId);
    }

    @Override
    public List<FaultTreeEntity> getAllFaultTrees() {
        return faultTreeMapper.findAll();
    }

    @Override
    public FaultTreeEntity updateFaultTree(String treeId, FaultTreeDTO faultTreeDTO, String userId) {
        try {
            // 查找现有故障树
            FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
            if (entity == null) {
                throw new RuntimeException("Fault tree not found");
            }
            
            // 更新字段
            entity.setName(faultTreeDTO.getEventName());
            entity.setDescription(faultTreeDTO.getDescription());
            entity.setEquipmentType(faultTreeDTO.getEquipmentType());
            entity.setTreeData(objectMapper.writeValueAsString(faultTreeDTO));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            
            // 保存更新
            faultTreeMapper.update(entity);
            return entity;
        } catch (Exception e) {
            log.error("Error updating fault tree: {}", e.getMessage());
            throw new RuntimeException("Failed to update fault tree", e);
        }
    }

    @Override
    public void deleteFaultTree(String treeId) {
        try {
            FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
            if (entity == null) {
                throw new RuntimeException("Fault tree not found");
            }
            faultTreeMapper.deleteByTreeId(treeId);
        } catch (Exception e) {
            log.error("Error deleting fault tree: {}", e.getMessage());
            throw new RuntimeException("Failed to delete fault tree", e);
        }
    }

    @Override
    public FaultTreeDTO convertToDTO(FaultTreeEntity entity) {
        try {
            return objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
        } catch (Exception e) {
            log.error("Error converting entity to DTO: {}", e.getMessage());
            throw new RuntimeException("Failed to convert entity to DTO", e);
        }
    }

    @Override
    public FaultTreeEntity convertToEntity(FaultTreeDTO dto) {
        try {
            FaultTreeEntity entity = new FaultTreeEntity();
            entity.setName(dto.getEventName());
            entity.setDescription(dto.getDescription());
            entity.setEquipmentType(dto.getEquipmentType());
            entity.setTreeData(objectMapper.writeValueAsString(dto));
            return entity;
        } catch (Exception e) {
            log.error("Error converting DTO to entity: {}", e.getMessage());
            throw new RuntimeException("Failed to convert DTO to entity", e);
        }
    }

    @Override
    public FaultTreeDTO addNode(String treeId, FaultTreeDTO parentNode, FaultTreeDTO newNode, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            if (parentNode == null) {
                if (root.getChildren() == null) {
                    root.setChildren(new java.util.ArrayList<>());
                }
                root.getChildren().add(newNode);
            } else {
                addNodeToParent(root, parentNode.getEventId(), newNode);
            }
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
            return root;
        } catch (Exception e) {
            log.error("Error adding node: {}", e.getMessage());
            throw new RuntimeException("Failed to add node", e);
        }
    }

    private boolean addNodeToParent(FaultTreeDTO node, String parentEventId, FaultTreeDTO newNode) {
        if (node.getEventId().equals(parentEventId)) {
            if (node.getChildren() == null) {
                node.setChildren(new java.util.ArrayList<>());
            }
            node.getChildren().add(newNode);
            return true;
        }
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                if (addNodeToParent(child, parentEventId, newNode)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public FaultTreeDTO updateNode(String treeId, FaultTreeDTO updatedNode, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            if (!updateNodeInTree(root, updatedNode)) {
                throw new RuntimeException("Node not found: " + updatedNode.getEventId());
            }
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
            return root;
        } catch (Exception e) {
            log.error("Error updating node: {}", e.getMessage());
            throw new RuntimeException("Failed to update node", e);
        }
    }

    private boolean updateNodeInTree(FaultTreeDTO node, FaultTreeDTO updatedNode) {
        if (node.getEventId().equals(updatedNode.getEventId())) {
            node.setEventName(updatedNode.getEventName());
            node.setDescription(updatedNode.getDescription());
            node.setEventType(updatedNode.getEventType());
            node.setGateType(updatedNode.getGateType());
            node.setSourceEvidence(updatedNode.getSourceEvidence());
            node.setEquipmentType(updatedNode.getEquipmentType());
            node.setConfidence(updatedNode.getConfidence());
            node.setVerificationStatus(updatedNode.getVerificationStatus());
            node.setAiGenerated(updatedNode.getAiGenerated());
            node.setPosition(updatedNode.getPosition());
            node.setExpanded(updatedNode.getExpanded());
            node.setSourceDetail(updatedNode.getSourceDetail());
            return true;
        }
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                if (updateNodeInTree(child, updatedNode)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void deleteNode(String treeId, String eventId, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            if (root.getEventId().equals(eventId)) {
                throw new RuntimeException("Cannot delete root node. Delete the entire fault tree instead.");
            }
            if (!deleteNodeFromParent(root, eventId)) {
                throw new RuntimeException("Node not found: " + eventId);
            }
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
        } catch (Exception e) {
            log.error("Error deleting node: {}", e.getMessage());
            throw new RuntimeException("Failed to delete node", e);
        }
    }

    private boolean deleteNodeFromParent(FaultTreeDTO node, String eventId) {
        if (node.getChildren() != null) {
            for (int i = 0; i < node.getChildren().size(); i++) {
                FaultTreeDTO child = node.getChildren().get(i);
                if (child.getEventId().equals(eventId)) {
                    node.getChildren().remove(i);
                    return true;
                }
                if (deleteNodeFromParent(child, eventId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public FaultTreeDTO moveNode(String treeId, String eventId, String newParentId, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            FaultTreeDTO movedNode = findNodeById(root, eventId);
            if (movedNode == null) {
                throw new RuntimeException("Node not found: " + eventId);
            }
            if (!deleteNodeFromParent(root, eventId)) {
                throw new RuntimeException("Failed to remove node from current position");
            }
            if (newParentId != null) {
                if (!addNodeToParent(root, newParentId, movedNode)) {
                    throw new RuntimeException("New parent not found: " + newParentId);
                }
            } else {
                if (root.getChildren() == null) {
                    root.setChildren(new java.util.ArrayList<>());
                }
                root.getChildren().add(movedNode);
            }
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
            return root;
        } catch (Exception e) {
            log.error("Error moving node: {}", e.getMessage());
            throw new RuntimeException("Failed to move node", e);
        }
    }

    private FaultTreeDTO findNodeById(FaultTreeDTO node, String eventId) {
        if (node.getEventId().equals(eventId)) {
            return node;
        }
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                FaultTreeDTO found = findNodeById(child, eventId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public void updateNodePositions(String treeId, Map<String, FaultTreeDTO.PositionDTO> positions, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            updatePositionsInNode(root, positions);
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
        } catch (Exception e) {
            log.error("Error updating positions: {}", e.getMessage());
            throw new RuntimeException("Failed to update node positions", e);
        }
    }

    private void updatePositionsInNode(FaultTreeDTO node, Map<String, FaultTreeDTO.PositionDTO> positions) {
        if (positions.containsKey(node.getEventId())) {
            node.setPosition(positions.get(node.getEventId()));
        }
        if (node.getChildren() != null) {
            for (FaultTreeDTO child : node.getChildren()) {
                updatePositionsInNode(child, positions);
            }
        }
    }

    @Override
    public FaultTreeDTO updateNodeConfidence(String treeId, String eventId, Double confidence, String verificationStatus, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            FaultTreeDTO node = findNodeById(root, eventId);
            if (node == null) {
                throw new RuntimeException("Node not found: " + eventId);
            }
            node.setConfidence(confidence);
            node.setVerificationStatus(verificationStatus);
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
            return root;
        } catch (Exception e) {
            log.error("Error updating node confidence: {}", e.getMessage());
            throw new RuntimeException("Failed to update node confidence", e);
        }
    }

    @Override
    public FaultTreeDTO updateNodeVerificationStatus(String treeId, String eventId, String verificationStatus, String userId) {
        FaultTreeEntity entity = faultTreeMapper.findByTreeId(treeId);
        if (entity == null) {
            throw new RuntimeException("Fault tree not found");
        }
        try {
            FaultTreeDTO root = objectMapper.readValue(entity.getTreeData(), FaultTreeDTO.class);
            FaultTreeDTO node = findNodeById(root, eventId);
            if (node == null) {
                throw new RuntimeException("Node not found: " + eventId);
            }
            node.setVerificationStatus(verificationStatus);
            entity.setTreeData(objectMapper.writeValueAsString(root));
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt();
            faultTreeMapper.update(entity);
            return root;
        } catch (Exception e) {
            log.error("Error updating verification status: {}", e.getMessage());
            throw new RuntimeException("Failed to update verification status", e);
        }
    }
}

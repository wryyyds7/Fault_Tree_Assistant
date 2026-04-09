package com.cxyaqcdm.fta.editor.service;

import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import java.util.List;
import java.util.Map;

public interface FaultTreeEditorService {
    FaultTreeEntity createFaultTree(FaultTreeDTO faultTreeDTO, String userId);
    FaultTreeEntity getFaultTree(String treeId);
    List<FaultTreeEntity> getAllFaultTrees();
    List<FaultTreeEntity> getFaultTreesByCreatedBy(String createdBy);
    FaultTreeEntity updateFaultTree(String treeId, FaultTreeDTO faultTreeDTO, String userId);
    void deleteFaultTree(String treeId);
    FaultTreeDTO convertToDTO(FaultTreeEntity entity);
    FaultTreeEntity convertToEntity(FaultTreeDTO dto);

    FaultTreeDTO addNode(String treeId, FaultTreeDTO parentNode, FaultTreeDTO newNode, String userId);
    FaultTreeDTO updateNode(String treeId, FaultTreeDTO updatedNode, String userId);
    void deleteNode(String treeId, String eventId, String userId);
    FaultTreeDTO moveNode(String treeId, String eventId, String newParentId, String userId);
    void updateNodePositions(String treeId, Map<String, FaultTreeDTO.PositionDTO> positions, String userId);
    FaultTreeDTO updateNodeConfidence(String treeId, String eventId, Double confidence, String verificationStatus, String userId);
    FaultTreeDTO updateNodeVerificationStatus(String treeId, String eventId, String verificationStatus, String userId);
}

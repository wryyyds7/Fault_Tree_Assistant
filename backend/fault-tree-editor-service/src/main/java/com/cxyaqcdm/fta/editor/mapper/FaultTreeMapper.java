package com.cxyaqcdm.fta.editor.mapper;

import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaultTreeMapper {
    FaultTreeEntity findById(@Param("id") Long id);
    FaultTreeEntity findByTreeId(@Param("treeId") String treeId);
    List<FaultTreeEntity> findAll();
    List<FaultTreeEntity> findByEquipmentType(@Param("equipmentType") String equipmentType);
    List<FaultTreeEntity> findByValidationStatus(@Param("validationStatus") String validationStatus);
    List<FaultTreeEntity> findByPublishStatus(@Param("publishStatus") String publishStatus);
    List<FaultTreeEntity> findByCreatedBy(@Param("createdBy") String createdBy);
    void insert(FaultTreeEntity faultTree);
    void update(FaultTreeEntity faultTree);
    void delete(@Param("id") Long id);
    void deleteByTreeId(@Param("treeId") String treeId);
}
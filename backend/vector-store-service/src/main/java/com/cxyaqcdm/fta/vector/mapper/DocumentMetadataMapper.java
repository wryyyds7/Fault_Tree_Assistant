package com.cxyaqcdm.fta.vector.mapper;

import com.cxyaqcdm.fta.vector.entity.DocumentMetadata;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocumentMetadataMapper {
    DocumentMetadata findById(Long id);
    DocumentMetadata findByDocId(String docId);
    List<DocumentMetadata> findAll();
    List<DocumentMetadata> findByUserId(String userId);
    List<DocumentMetadata> findByEquipmentType(String equipmentType);
    List<String> findDistinctEquipmentTypes();
    void insert(DocumentMetadata documentMetadata);
    void update(DocumentMetadata documentMetadata);
    void delete(String docId);
}
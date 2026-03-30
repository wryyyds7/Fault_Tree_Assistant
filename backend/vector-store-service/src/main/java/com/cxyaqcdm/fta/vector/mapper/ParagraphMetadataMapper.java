package com.cxyaqcdm.fta.vector.mapper;

import com.cxyaqcdm.fta.vector.entity.ParagraphMetadata;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ParagraphMetadataMapper {
    ParagraphMetadata findById(Long id);
    ParagraphMetadata findByParagraphId(String paragraphId);
    List<ParagraphMetadata> findByDocId(String docId);
    void insert(ParagraphMetadata paragraphMetadata);
    void update(ParagraphMetadata paragraphMetadata);
    void deleteByDocId(String docId);
}
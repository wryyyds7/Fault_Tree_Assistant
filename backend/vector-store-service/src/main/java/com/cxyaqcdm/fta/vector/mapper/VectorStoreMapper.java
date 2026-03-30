package com.cxyaqcdm.fta.vector.mapper;

import com.cxyaqcdm.fta.vector.entity.VectorStore;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VectorStoreMapper {
    VectorStore findById(Long id);
    VectorStore findByVectorId(String vectorId);
    VectorStore findByParagraphId(String paragraphId);
    List<VectorStore> findByDocId(String docId);
    void insert(VectorStore vectorStore);
    void update(VectorStore vectorStore);
    void deleteByDocId(String docId);
}
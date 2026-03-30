package com.cxyaqcdm.fta.editor.mapper;

import com.cxyaqcdm.fta.editor.entity.FaultTreeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaultTreeMapper {
    FaultTreeEntity findById(@Param("id") String id);
    List<FaultTreeEntity> findAll();
    void insert(FaultTreeEntity faultTree);
    void update(FaultTreeEntity faultTree);
    void delete(@Param("id") String id);
}
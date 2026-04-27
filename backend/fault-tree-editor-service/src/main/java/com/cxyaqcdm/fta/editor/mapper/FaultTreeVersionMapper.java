package com.cxyaqcdm.fta.editor.mapper;

import com.cxyaqcdm.fta.editor.entity.FaultTreeVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaultTreeVersionMapper {
    List<FaultTreeVersionEntity> findByTreeId(@Param("treeId") String treeId);
    FaultTreeVersionEntity findByTreeIdAndVersionNumber(@Param("treeId") String treeId, @Param("versionNumber") Integer versionNumber);
    FaultTreeVersionEntity findLatestByTreeId(@Param("treeId") String treeId);
    Integer getMaxVersionNumber(@Param("treeId") String treeId);
    Integer getVersionCount(@Param("treeId") String treeId);
    void insert(FaultTreeVersionEntity version);
    void deleteByTreeId(@Param("treeId") String treeId);
    void deleteOldVersions(@Param("treeId") String treeId, @Param("deleteCount") int deleteCount);
}

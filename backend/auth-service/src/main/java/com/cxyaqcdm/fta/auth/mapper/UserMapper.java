package com.cxyaqcdm.fta.auth.mapper;

import com.cxyaqcdm.fta.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserEntity findByUsername(@Param("username") String username);
    UserEntity findByEmail(@Param("email") String email);
    void insert(UserEntity user);
}
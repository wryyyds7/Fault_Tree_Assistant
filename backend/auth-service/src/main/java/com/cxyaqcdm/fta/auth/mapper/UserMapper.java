package com.cxyaqcdm.fta.auth.mapper;

import com.cxyaqcdm.fta.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    UserEntity findById(@Param("id") Long id);
    UserEntity findByUserId(@Param("userId") String userId);
    UserEntity findByUsername(@Param("username") String username);
    UserEntity findByEmail(@Param("email") String email);
    List<UserEntity> findAll();
    void insert(UserEntity user);
    void update(UserEntity user);
    void updateLastLoginTime(@Param("userId") String userId, @Param("lastLoginTime") java.time.LocalDateTime lastLoginTime);
    void updateFailedLoginAttempts(@Param("userId") String userId, @Param("failedLoginAttempts") Integer failedLoginAttempts);
    void delete(@Param("userId") String userId);
}
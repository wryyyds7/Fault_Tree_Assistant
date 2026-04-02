package com.cxyaqcdm.fta.auth.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserEntity {
    private Long id;
    private String userId;
    private String username;
    private String password;
    private String email;
    private String role;
    private LocalDateTime lastLoginTime;
    private LocalDateTime passwordChangeTime;
    private Integer failedLoginAttempts;
    private LocalDateTime lockTime;
    private String passwordHistory;
    private LocalDateTime createdAt;

    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}

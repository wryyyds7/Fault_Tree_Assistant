package com.cxyaqcdm.fta.auth.service;

import com.cxyaqcdm.fta.auth.dto.LoginRequest;
import com.cxyaqcdm.fta.auth.dto.LoginResponse;
import com.cxyaqcdm.fta.auth.dto.RegisterRequest;
import com.cxyaqcdm.fta.auth.entity.UserEntity;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    UserEntity register(RegisterRequest registerRequest);
    UserEntity getUserByUsername(String username);
    String generateToken(UserEntity user);
    String generateRefreshToken(UserEntity user);
    boolean validateToken(String token);
    String getUsernameFromToken(String token);
}
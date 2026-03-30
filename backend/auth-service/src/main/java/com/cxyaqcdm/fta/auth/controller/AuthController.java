package com.cxyaqcdm.fta.auth.controller;

import com.cxyaqcdm.fta.auth.dto.LoginRequest;
import com.cxyaqcdm.fta.auth.dto.LoginResponse;
import com.cxyaqcdm.fta.auth.dto.RegisterRequest;
import com.cxyaqcdm.fta.auth.entity.UserEntity;
import com.cxyaqcdm.fta.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserEntity> register(@RequestBody RegisterRequest registerRequest) {
        UserEntity user = authService.register(registerRequest);
        return ResponseEntity.ok(user);
    }
}
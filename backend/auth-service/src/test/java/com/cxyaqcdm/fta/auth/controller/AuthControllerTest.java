package com.cxyaqcdm.fta.auth.controller;

import com.cxyaqcdm.fta.auth.dto.LoginRequest;
import com.cxyaqcdm.fta.auth.dto.LoginResponse;
import com.cxyaqcdm.fta.auth.dto.RegisterRequest;
import com.cxyaqcdm.fta.auth.entity.UserEntity;
import com.cxyaqcdm.fta.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    public void testLogin() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("test");
        loginRequest.setPassword("password");

        LoginResponse loginResponse = new LoginResponse();


        when(authService.login(loginRequest)).thenReturn(loginResponse);

        // Act
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("test-token", response.getBody().getAccessToken());
        assertEquals("refresh-token", response.getBody().getRefreshToken());
        assertEquals("Bearer", response.getBody().getTokenType());
        assertEquals(3600, response.getBody().getExpiresIn());

    }

    @Test
    public void testRegister() {
        // Arrange
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("test");
        registerRequest.setPassword("password");
        registerRequest.setEmail("test@example.com");

        UserEntity userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("test");
        userEntity.setEmail("test@example.com");

        when(authService.register(registerRequest)).thenReturn(userEntity);

        // Act
        ResponseEntity<UserEntity> response = authController.register(registerRequest);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1L, response.getBody().getId());
        assertEquals("test", response.getBody().getUsername());
        assertEquals("test@example.com", response.getBody().getEmail());
    }
}

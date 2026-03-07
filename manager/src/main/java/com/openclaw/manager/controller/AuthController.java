package com.openclaw.manager.controller;

import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.dto.ApiResponse;
import com.openclaw.manager.dto.LoginRequest;
import com.openclaw.manager.dto.RegisterRequest;
import com.openclaw.manager.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", Map.of(
                "userId", user.getId(),
                "username", user.getUsername()
        )));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(@Valid @RequestBody LoginRequest req) {
        String token = authService.login(req.getUsername(), req.getPassword());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
    }
}

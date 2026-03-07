package com.openclaw.portal.controller;

import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {

    private static final String COOKIE_NAME = "openclaw_token";
    private static final int COOKIE_MAX_AGE = 86400; // 24h

    private final ManagerClient managerClient;

    public AuthController(ManagerClient managerClient) {
        this.managerClient = managerClient;
    }

    @PostMapping("/api/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        try {
            Map<String, Object> result = managerClient.register(username, password);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/portal/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");

        try {
            String token = managerClient.login(username, password);

            Cookie cookie = new Cookie(COOKIE_NAME, token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(COOKIE_MAX_AGE);
            response.addCookie(cookie);

            return ResponseEntity.ok(Map.of("success", true, "message", "Login successful"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid credentials"));
        }
    }

    @GetMapping("/portal/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out"));
    }
}

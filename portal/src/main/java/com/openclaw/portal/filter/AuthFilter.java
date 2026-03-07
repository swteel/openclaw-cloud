package com.openclaw.portal.filter;

import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@Order(1)
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private static final String COOKIE_NAME = "openclaw_token";

    private final ManagerClient managerClient;

    public AuthFilter(ManagerClient managerClient) {
        this.managerClient = managerClient;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();

        // Allow login/logout without auth
        if (path.equals("/portal/login") || path.equals("/portal/logout")) {
            chain.doFilter(req, res);
            return;
        }

        // Only protect /app, /app/** and /portal/upload/**
        if (!path.equals("/app") && !path.startsWith("/app/") && !path.startsWith("/portal/upload/")) {
            chain.doFilter(req, res);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication token");
            return;
        }

        try {
            Map<String, Object> userInfo = managerClient.verifyToken(token);
            Long userId = ((Number) userInfo.get("userId")).longValue();

            request.setAttribute("userId", userId);
            request.setAttribute("username", userInfo.get("username"));

            // Send heartbeat asynchronously (best-effort)
            try {
                managerClient.heartbeat(userId);
            } catch (Exception e) {
                log.warn("Heartbeat failed for user {}: {}", userId, e.getMessage());
            }

            chain.doFilter(req, res);
        } catch (Exception e) {
            log.warn("Auth failed for path {}: {}", path, e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private String extractToken(HttpServletRequest request) {
        // Check cookie first
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // Fallback: Authorization header
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

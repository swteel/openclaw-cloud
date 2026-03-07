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

    // Sensitive paths in /app that non-admin users cannot access
    private static final String[] BLOCKED_PATHS = {
        "/app/config", "/app/settings", "/app/providers",
        "/app/nodes", "/app/cron", "/app/debug", "/app/usage"
    };

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

        // Only protect /app, /app/**, /portal/upload/**, /portal/files/**, /api/admin/**, /api/containers/**
        boolean isProtected = path.equals("/app") || path.startsWith("/app/")
                || path.startsWith("/portal/upload/")
                || path.equals("/portal/files") || path.startsWith("/portal/files/")
                || path.startsWith("/api/admin/") || path.equals("/api/admin")
                || path.startsWith("/api/containers/") || path.equals("/api/containers")
                || path.startsWith("/admin-proxy/");
        if (!isProtected) {
            chain.doFilter(req, res);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication token");
            return;
        }

        String role;
        Long userId;
        try {
            Map<String, Object> userInfo = managerClient.verifyToken(token);
            userId = ((Number) userInfo.get("userId")).longValue();
            role = String.valueOf(userInfo.get("role"));
        } catch (Exception e) {
            log.warn("Auth failed for path {}: {}", path, e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        request.setAttribute("userId", userId);
        request.setAttribute("username", "");
        request.setAttribute("role", role);
        request.setAttribute("token", token);

        // Block sensitive paths for non-admin users
        if (!"ADMIN".equals(role)) {
            for (String blocked : BLOCKED_PATHS) {
                if (path.startsWith(blocked)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                    return;
                }
            }
        }

        // Send heartbeat asynchronously (best-effort)
        try {
            managerClient.heartbeat(userId);
        } catch (Exception e) {
            log.warn("Heartbeat failed for user {}: {}", userId, e.getMessage());
        }

        chain.doFilter(req, res);
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

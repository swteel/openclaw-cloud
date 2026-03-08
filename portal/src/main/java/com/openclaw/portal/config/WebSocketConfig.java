package com.openclaw.portal.config;

import com.openclaw.portal.handler.WsProxyHandler;
import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsProxyHandler wsProxyHandler;
    private final ManagerClient managerClient;

    public WebSocketConfig(WsProxyHandler wsProxyHandler, ManagerClient managerClient) {
        this.wsProxyHandler = wsProxyHandler;
        this.managerClient = managerClient;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Kept for WebSocket infrastructure initialization, but actual routing
        // is handled by wsUpgradeHandlerMapping() below at higher priority.
        registry.addHandler(wsProxyHandler, "/app", "/app/")
                .addInterceptors(new AuthHandshakeInterceptor(managerClient))
                .setAllowedOriginPatterns("*");
        registry.addHandler(wsProxyHandler, "/admin-proxy/**")
                .addInterceptors(new AdminProxyHandshakeInterceptor(managerClient))
                .setAllowedOriginPatterns("*");
        // Container-specific WebSocket: /app-c/{containerName}/**
        registry.addHandler(wsProxyHandler, "/app-c/**")
                .addInterceptors(new ContainerProxyHandshakeInterceptor(managerClient))
                .setAllowedOriginPatterns("*");
    }

    /**
     * High-priority HandlerMapping that intercepts WebSocket upgrade requests
     * to /app before AppProxyController can handle them as regular HTTP.
     */
    @Bean
    public AbstractHandlerMapping wsUpgradeHandlerMapping() {
        AuthHandshakeInterceptor interceptor = new AuthHandshakeInterceptor(managerClient);
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();

        WebSocketHttpRequestHandler wsRequestHandler =
                new WebSocketHttpRequestHandler(wsProxyHandler, handshakeHandler);
        wsRequestHandler.setHandshakeInterceptors(List.of(interceptor));

        return new AbstractHandlerMapping() {
            {
                setOrder(-1); // Before RequestMappingHandlerMapping (order 0)
            }

            @Override
            protected Object getHandlerInternal(HttpServletRequest request) {
                String uri = request.getRequestURI();
                String upgrade = request.getHeader("Upgrade");
                if (!"websocket".equalsIgnoreCase(upgrade)) return null;
                // User proxy WS
                if (uri.equals("/app") || uri.equals("/app/")) return wsRequestHandler;
                // Admin proxy WS: /admin-proxy/{uid}/...
                if (uri.startsWith("/admin-proxy/")) {
                    AdminProxyHandshakeInterceptor adminInterceptor =
                            new AdminProxyHandshakeInterceptor(managerClient);
                    WebSocketHttpRequestHandler adminWsHandler =
                            new WebSocketHttpRequestHandler(wsProxyHandler, new DefaultHandshakeHandler());
                    adminWsHandler.setHandshakeInterceptors(List.of(adminInterceptor));
                    return adminWsHandler;
                }
                // Container-specific WS: /app-c/{containerName}/...
                if (uri.startsWith("/app-c/")) {
                    ContainerProxyHandshakeInterceptor containerInterceptor =
                            new ContainerProxyHandshakeInterceptor(managerClient);
                    WebSocketHttpRequestHandler containerWsHandler =
                            new WebSocketHttpRequestHandler(wsProxyHandler, new DefaultHandshakeHandler());
                    containerWsHandler.setHandshakeInterceptors(List.of(containerInterceptor));
                    return containerWsHandler;
                }
                return null;
            }
        };
    }

    static class AuthHandshakeInterceptor implements HandshakeInterceptor {
        private static final String COOKIE_NAME = "openclaw_token";
        private final ManagerClient managerClient;

        AuthHandshakeInterceptor(ManagerClient managerClient) {
            this.managerClient = managerClient;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                Cookie[] cookies = servletRequest.getServletRequest().getCookies();
                String token = null;

                if (cookies != null) {
                    Optional<Cookie> cookie = Arrays.stream(cookies)
                            .filter(c -> COOKIE_NAME.equals(c.getName()))
                            .findFirst();
                    if (cookie.isPresent()) {
                        token = cookie.get().getValue();
                    }
                }

                if (token == null) {
                    String auth = servletRequest.getServletRequest().getHeader("Authorization");
                    if (auth != null && auth.startsWith("Bearer ")) {
                        token = auth.substring(7);
                    }
                }

                // Also check URL query parameter for token (from iframe src with ?token=xxx)
                if (token == null) {
                    String query = servletRequest.getServletRequest().getQueryString();
                    if (query != null) {
                        String[] params = query.split("&");
                        for (String param : params) {
                            if (param.startsWith("token=")) {
                                token = param.substring(6);
                                break;
                            }
                        }
                    }
                }

                if (token != null) {
                    try {
                        Map<String, Object> userInfo = managerClient.verifyToken(token);
                        Long userId = ((Number) userInfo.get("userId")).longValue();
                        attributes.put("userId", userId);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }

    /**
     * Handshake interceptor for /admin-proxy/{uid}/** WebSocket connections.
     * Verifies ADMIN role and extracts the target uid from the URL path.
     */
    static class AdminProxyHandshakeInterceptor implements HandshakeInterceptor {
        private static final String COOKIE_NAME = "openclaw_token";
        private final ManagerClient managerClient;

        AdminProxyHandshakeInterceptor(ManagerClient managerClient) {
            this.managerClient = managerClient;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;

            // Extract token from cookie or header
            String token = null;
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                token = Arrays.stream(cookies)
                        .filter(c -> COOKIE_NAME.equals(c.getName()))
                        .map(Cookie::getValue).findFirst().orElse(null);
            }
            if (token == null) {
                String auth = servletRequest.getServletRequest().getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7);
            }
            
            // Also check URL query parameter for token
            if (token == null) {
                String query = servletRequest.getServletRequest().getQueryString();
                if (query != null) {
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("token=")) {
                            token = param.substring(6);
                            break;
                        }
                    }
                }
            }
            if (token == null) return false;

            try {
                Map<String, Object> userInfo = managerClient.verifyToken(token);
                if (!"ADMIN".equals(userInfo.get("role"))) return false;

                Long userId = ((Number) userInfo.get("userId")).longValue();
                attributes.put("userId", userId);

                // Extract target uid from path: /admin-proxy/{uid}/...
                String path = servletRequest.getServletRequest().getRequestURI();
                String[] parts = path.split("/");
                if (parts.length >= 3) {
                    Long targetUserId = Long.parseLong(parts[2]);
                    attributes.put("targetUserId", targetUserId);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }

    /**
     * Handshake interceptor for /app-c/{containerName}/** WebSocket connections.
     * Extracts containerName from path and JWT token from cookie/header (NOT from query param).
     * Note: The ?token=xxx in URL is gateway token for OpenClaw container, not JWT for portal auth.
     */
    static class ContainerProxyHandshakeInterceptor implements HandshakeInterceptor {
        private static final String COOKIE_NAME = "openclaw_token";
        private final ManagerClient managerClient;

        ContainerProxyHandshakeInterceptor(ManagerClient managerClient) {
            this.managerClient = managerClient;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;

            // Extract containerName from path: /app-c/{containerName}/...
            String path = servletRequest.getServletRequest().getRequestURI();
            String afterPrefix = path.replaceFirst("^/app-c/", "");
            int slash = afterPrefix.indexOf('/');
            String containerName = slash >= 0 ? afterPrefix.substring(0, slash) : afterPrefix;
            if (containerName == null || containerName.isEmpty()) return false;
            attributes.put("containerName", containerName);

            // Extract JWT token from cookie or header ONLY (NOT from query param)
            // Query param ?token=xxx is gateway token, not JWT
            String token = null;
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                token = Arrays.stream(cookies)
                        .filter(c -> COOKIE_NAME.equals(c.getName()))
                        .map(Cookie::getValue).findFirst().orElse(null);
            }
            if (token == null) {
                String auth = servletRequest.getServletRequest().getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7);
            }
            if (token == null) return false;

            try {
                Map<String, Object> userInfo = managerClient.verifyToken(token);
                Long userId = ((Number) userInfo.get("userId")).longValue();
                attributes.put("userId", userId);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}

package com.openclaw.portal.config;

import com.openclaw.portal.handler.WsProxyHandler;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsProxyHandler wsProxyHandler;
    private final com.openclaw.portal.service.ManagerClient managerClient;

    public WebSocketConfig(WsProxyHandler wsProxyHandler,
                           com.openclaw.portal.service.ManagerClient managerClient) {
        this.wsProxyHandler = wsProxyHandler;
        this.managerClient = managerClient;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsProxyHandler, "/app", "/app/")
                .addInterceptors(new AuthHandshakeInterceptor(managerClient))
                .setAllowedOriginPatterns("*");
    }

    private static class AuthHandshakeInterceptor implements HandshakeInterceptor {
        private static final String COOKIE_NAME = "openclaw_token";
        private final com.openclaw.portal.service.ManagerClient managerClient;

        AuthHandshakeInterceptor(com.openclaw.portal.service.ManagerClient managerClient) {
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
}

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
                // Only intercept WebSocket upgrade requests to /app or /app/
                String uri = request.getRequestURI();
                String upgrade = request.getHeader("Upgrade");
                if ("websocket".equalsIgnoreCase(upgrade)
                        && (uri.equals("/app") || uri.equals("/app/"))) {
                    return wsRequestHandler;
                }
                return null; // Let other handlers (AppProxyController) handle it
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

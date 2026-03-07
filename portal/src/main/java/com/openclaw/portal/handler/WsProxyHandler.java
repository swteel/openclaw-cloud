package com.openclaw.portal.handler;

import com.openclaw.portal.service.ManagerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class WsProxyHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WsProxyHandler.class);

    private final ManagerClient managerClient;
    private final StandardWebSocketClient wsClient;

    public WsProxyHandler(ManagerClient managerClient) {
        this.managerClient = managerClient;
        this.wsClient = new StandardWebSocketClient();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
        Long userId = (Long) clientSession.getAttributes().get("userId");
        if (userId == null) {
            clientSession.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
            return;
        }

        String[] containerInfo = managerClient.getContainerInfo(userId);
        String address = containerInfo[0];
        String gatewayToken = containerInfo[1];
        String path = clientSession.getUri().getPath().replaceFirst("^/app", "");
        String query = clientSession.getUri().getQuery();
        String targetUri = "ws://" + address + (path.isEmpty() ? "/" : path)
                + (query != null ? "?" + query : "");

        log.debug("WS proxy: user {} -> {}", userId, targetUri);

        BackendWsHandler backendHandler = new BackendWsHandler(clientSession);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (gatewayToken != null && !gatewayToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + gatewayToken);
        }

        wsClient.execute(backendHandler, headers, URI.create(targetUri))
                .thenAccept(backendSession -> {
                    clientSession.getAttributes().put("backendSession", backendSession);
                    log.info("WS proxy established for user {}", userId);
                })
                .exceptionally(ex -> {
                    log.error("WS backend connection failed for user {}: {}", userId, ex.getMessage());
                    try {
                        clientSession.close(CloseStatus.SERVER_ERROR);
                    } catch (IOException e) {
                        log.warn("Error closing client WS session: {}", e.getMessage());
                    }
                    return null;
                });
    }

    @Override
    protected void handleTextMessage(WebSocketSession clientSession, TextMessage message) throws Exception {
        WebSocketSession backendSession = (WebSocketSession) clientSession.getAttributes().get("backendSession");
        if (backendSession != null && backendSession.isOpen()) {
            backendSession.sendMessage(message);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession clientSession, BinaryMessage message) throws Exception {
        WebSocketSession backendSession = (WebSocketSession) clientSession.getAttributes().get("backendSession");
        if (backendSession != null && backendSession.isOpen()) {
            backendSession.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) throws Exception {
        WebSocketSession backendSession = (WebSocketSession) clientSession.getAttributes().get("backendSession");
        if (backendSession != null && backendSession.isOpen()) {
            backendSession.close(status);
        }
        Long userId = (Long) clientSession.getAttributes().get("userId");
        log.info("WS client disconnected for user {}, status: {}", userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) throws Exception {
        Long userId = (Long) clientSession.getAttributes().get("userId");
        log.warn("WS transport error for user {}: {}", userId, exception.getMessage());
        if (clientSession.isOpen()) {
            clientSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Handles messages from backend container and forwards to client.
     */
    private static class BackendWsHandler extends AbstractWebSocketHandler {
        private final WebSocketSession clientSession;

        BackendWsHandler(WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            if (clientSession.isOpen()) {
                clientSession.sendMessage(message);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
            if (clientSession.isOpen()) {
                clientSession.sendMessage(message);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            if (clientSession.isOpen()) {
                clientSession.close(status);
            }
        }
    }
}

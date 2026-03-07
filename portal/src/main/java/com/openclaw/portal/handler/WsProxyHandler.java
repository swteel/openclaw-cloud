package com.openclaw.portal.handler;

import com.openclaw.portal.service.ManagerClient;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Proxies WebSocket connections from the browser to the user's openclaw container.
 *
 * Uses OkHttp (instead of Tyrus/StandardWebSocketClient) so we can freely set
 * the Host and Origin headers.  This makes openclaw treat our proxy as a local
 * client (isLocalClient = true), which:
 *   - Satisfies the Origin check without patching openclaw JS (Bug 1)
 *   - Enables silent auto-pairing on first connect       (Bug 3)
 */
@Component
public class WsProxyHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WsProxyHandler.class);

    // Fake local origin so openclaw's isLocalishHost() returns true
    private static final String LOCAL_HOST_HEADER  = "localhost:18789";
    private static final String LOCAL_ORIGIN_HEADER = "http://localhost:18789";

    private final ManagerClient managerClient;
    private final OkHttpClient okHttpClient;

    public WsProxyHandler(ManagerClient managerClient) {
        this.managerClient = managerClient;
        this.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)   // no read timeout for persistent WS
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
        Long userId = (Long) clientSession.getAttributes().get("userId");
        if (userId == null) {
            clientSession.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
            return;
        }

        String[] containerInfo = managerClient.getContainerInfo(userId);
        String address      = containerInfo[0];
        String gatewayToken = containerInfo[1];

        String path  = clientSession.getUri().getPath().replaceFirst("^/app", "");
        String query = clientSession.getUri().getQuery();
        String wsUrl = "ws://" + address + (path.isEmpty() ? "/" : path)
                + (query != null ? "?" + query : "");

        log.debug("WS proxy: user {} -> {}", userId, wsUrl);

        Request.Builder reqBuilder = new Request.Builder()
                .url(wsUrl)
                // Pretend we're a local browser so openclaw allows the connection
                // and auto-approves device pairing (no JS patching needed).
                .header("Host", LOCAL_HOST_HEADER)
                .header("Origin", LOCAL_ORIGIN_HEADER)
                // Tell openclaw the real client is 127.0.0.1 (via trusted proxy config)
                .header("X-Forwarded-For", "127.0.0.1");

        if (gatewayToken != null && !gatewayToken.isBlank()) {
            reqBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + gatewayToken);
        }

        okHttpClient.newWebSocket(reqBuilder.build(), new WebSocketListener() {

            @Override
            public void onOpen(okhttp3.WebSocket backendWs, Response response) {
                clientSession.getAttributes().put("backendWs", backendWs);
                log.info("WS proxy established for user {}", userId);
            }

            @Override
            public void onMessage(okhttp3.WebSocket backendWs, String text) {
                try {
                    if (clientSession.isOpen()) clientSession.sendMessage(new TextMessage(text));
                } catch (IOException e) {
                    log.warn("Error forwarding text to client user {}: {}", userId, e.getMessage());
                }
            }

            @Override
            public void onMessage(okhttp3.WebSocket backendWs, ByteString bytes) {
                try {
                    if (clientSession.isOpen())
                        clientSession.sendMessage(new BinaryMessage(bytes.toByteArray()));
                } catch (IOException e) {
                    log.warn("Error forwarding binary to client user {}: {}", userId, e.getMessage());
                }
            }

            @Override
            public void onClosed(okhttp3.WebSocket backendWs, int code, String reason) {
                try {
                    if (clientSession.isOpen()) clientSession.close(new CloseStatus(code, reason));
                } catch (IOException e) {
                    log.warn("Error closing client session for user {}: {}", userId, e.getMessage());
                }
                log.info("WS backend closed for user {}, code={} reason={}", userId, code, reason);
            }

            @Override
            public void onFailure(okhttp3.WebSocket backendWs, Throwable t, Response response) {
                log.error("WS backend connection failed for user {}: {}", userId, t.getMessage());
                try {
                    if (clientSession.isOpen()) clientSession.close(CloseStatus.SERVER_ERROR);
                } catch (IOException e) {
                    log.warn("Error closing client session after backend failure: {}", e.getMessage());
                }
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession clientSession, TextMessage message) {
        okhttp3.WebSocket backendWs = (okhttp3.WebSocket) clientSession.getAttributes().get("backendWs");
        if (backendWs != null) backendWs.send(message.getPayload());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession clientSession, BinaryMessage message) {
        okhttp3.WebSocket backendWs = (okhttp3.WebSocket) clientSession.getAttributes().get("backendWs");
        if (backendWs != null) backendWs.send(ByteString.of(message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) {
        okhttp3.WebSocket backendWs = (okhttp3.WebSocket) clientSession.getAttributes().get("backendWs");
        if (backendWs != null) backendWs.close(status.getCode(),
                status.getReason() != null ? status.getReason() : "");
        log.info("WS client disconnected for user {}, status: {}",
                clientSession.getAttributes().get("userId"), status);
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) {
        log.warn("WS transport error for user {}: {}",
                clientSession.getAttributes().get("userId"), exception.getMessage());
        try {
            if (clientSession.isOpen()) clientSession.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.warn("Error closing client session on transport error: {}", e.getMessage());
        }
    }
}

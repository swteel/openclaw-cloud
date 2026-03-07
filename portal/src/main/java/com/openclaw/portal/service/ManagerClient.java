package com.openclaw.portal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class ManagerClient {

    private final WebClient internalClient;   // has X-Internal-Token header
    private final WebClient publicClient;     // no internal token

    public ManagerClient(@Value("${portal.manager-url}") String managerUrl,
                         @Value("${portal.internal-token}") String internalToken) {
        this.internalClient = WebClient.builder()
                .baseUrl(managerUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
        this.publicClient = WebClient.builder()
                .baseUrl(managerUrl)
                .build();
    }

    /**
     * Verify a JWT token via internal endpoint. Returns user info map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verifyToken(String token) {
        Map<String, Object> response = internalClient.post()
                .uri("/internal/auth/verify")
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new RuntimeException("Token verification failed");
        }
        return (Map<String, Object>) response.get("data");
    }

    /**
     * Get container address and gateway token for a user.
     * Returns [address, gatewayToken].
     */
    @SuppressWarnings("unchecked")
    public String[] getContainerInfo(Long userId) {
        Map<String, Object> response = internalClient.get()
                .uri("/internal/containers/{uid}/address", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new RuntimeException("Failed to get container info for user " + userId);
        }
        Map<String, String> data = (Map<String, String>) response.get("data");
        return new String[]{data.get("address"), data.get("gatewayToken")};
    }

    /**
     * Send heartbeat and wake container if needed.
     */
    @SuppressWarnings("unchecked")
    public void heartbeat(Long userId) {
        internalClient.post()
                .uri("/internal/users/{uid}/heartbeat", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Forward register to manager's public endpoint.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> register(String username, String password) {
        Map<String, Object> response = publicClient.post()
                .uri("/api/auth/register")
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("No response from manager");
        }
        return response;
    }

    /**
     * Proxy an arbitrary HTTP request to manager with user's JWT token.
     */
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> proxyRequest(String method, String path, String token, Object body) {
        try {
            WebClient.RequestBodySpec spec = publicClient.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .header("Authorization", "Bearer " + token);
            WebClient.ResponseSpec response;
            if (body != null) {
                response = spec.bodyValue(body).retrieve();
            } else {
                response = spec.retrieve();
            }
            Object result = response.bodyToMono(Object.class).block();
            return ResponseEntity.ok(result);
        } catch (WebClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Forward login to manager's public endpoint and get JWT.
     */
    @SuppressWarnings("unchecked")
    public String login(String username, String password) {
        Map<String, Object> response = publicClient.post()
                .uri("/api/auth/login")
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new RuntimeException("Login failed");
        }
        Map<String, String> data = (Map<String, String>) response.get("data");
        return data.get("token");
    }
}

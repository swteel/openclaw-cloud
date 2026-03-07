package com.openclaw.portal.handler;

import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

@Component
public class ProxyHandler {

    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = new HashSet<>(Arrays.asList(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade",
            "host", "content-length"
    ));

    private final ManagerClient managerClient;
    private final HttpClient httpClient;

    public ProxyHandler(ManagerClient managerClient) {
        this.managerClient = managerClient;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long userId = (Long) request.getAttribute("userId");
        String[] containerInfo = managerClient.getContainerInfo(userId);
        String address = containerInfo[0];
        String gatewayToken = containerInfo[1];

        String path = request.getRequestURI().replaceFirst("^/app", "");
        String query = request.getQueryString();
        String targetUrl = "http://" + address + (path.isEmpty() ? "/" : path)
                + (query != null ? "?" + query : "");

        log.debug("Proxying {} {} -> {}", request.getMethod(), request.getRequestURI(), targetUrl);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        // Copy request headers (skip hop-by-hop and authorization - we'll inject our own)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement().toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(name) && !"authorization".equals(name)) {
                builder.header(name, request.getHeader(name));
            }
        }
        // Inject gateway token for openclaw auth
        if (gatewayToken != null && !gatewayToken.isBlank()) {
            builder.header("Authorization", "Bearer " + gatewayToken);
        }
        // Set method and body
        String method = request.getMethod();
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            byte[] body = request.getInputStream().readAllBytes();
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }

        try {
            HttpResponse<InputStream> upstreamResponse = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            response.setStatus(upstreamResponse.statusCode());

            // Copy response headers, stripping iframe-blocking headers
            upstreamResponse.headers().map().forEach((name, values) -> {
                String nameLower = name.toLowerCase();
                if (HOP_BY_HOP_HEADERS.contains(nameLower)) return;
                // Remove X-Frame-Options so the iframe can embed the app
                if ("x-frame-options".equals(nameLower)) return;
                if ("content-security-policy".equals(nameLower)) {
                    // Strip frame-ancestors directive from CSP
                    values.forEach(v -> {
                        String stripped = v.replaceAll("(?i)frame-ancestors[^;]*(;|$)", "").trim();
                        if (!stripped.isEmpty()) response.addHeader(name, stripped);
                    });
                    return;
                }
                values.forEach(v -> response.addHeader(name, v));
            });

            // Stream response body
            try (InputStream body = upstreamResponse.body()) {
                body.transferTo(response.getOutputStream());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy interrupted");
        } catch (Exception e) {
            log.error("Proxy error for user {} -> {}: {}", userId, targetUrl, e.toString(), e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e);
        }
    }
}

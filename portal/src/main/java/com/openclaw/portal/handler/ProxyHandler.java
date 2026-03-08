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
import java.nio.charset.StandardCharsets;
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
        proxyTo(request, response, (Long) request.getAttribute("userId"), "/app");
    }

    public void proxyToByAddress(HttpServletRequest request, HttpServletResponse response,
                                  String address, String gatewayToken, String pathPrefix) throws IOException {
        String path = request.getRequestURI().replaceFirst("^" + pathPrefix, "");
        String query = request.getQueryString();
        String targetUrl = "http://" + address + (path.isEmpty() ? "/" : path)
                + (query != null ? "?" + query : "");

        log.debug("Proxying {} {} -> {}", request.getMethod(), request.getRequestURI(), targetUrl);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement().toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(name) && !"authorization".equals(name)) {
                builder.header(name, request.getHeader(name));
            }
        }
        if (gatewayToken != null && !gatewayToken.isBlank()) {
            builder.header("Authorization", "Bearer " + gatewayToken);
        }
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

            upstreamResponse.headers().map().forEach((name, values) -> {
                String nameLower = name.toLowerCase();
                if (HOP_BY_HOP_HEADERS.contains(nameLower)) return;
                if ("x-frame-options".equals(nameLower)) return;
                if ("content-security-policy".equals(nameLower)) return;
                values.forEach(v -> response.addHeader(name, v));
            });

            try (InputStream body = upstreamResponse.body()) {
                String ct = upstreamResponse.headers().firstValue("content-type").orElse("");
                if (ct.startsWith("text/html")) {
                    byte[] bytes = body.readAllBytes();
                    String html = new String(bytes, StandardCharsets.UTF_8);
                    html = injectHideScript(html);
                    byte[] out = html.getBytes(StandardCharsets.UTF_8);
                    response.setContentLength(out.length);
                    response.getOutputStream().write(out);
                } else {
                    body.transferTo(response.getOutputStream());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy interrupted");
        } catch (Exception e) {
            log.error("Proxy error -> {}: {}", targetUrl, e.toString(), e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e);
        }
    }

    public void proxyTo(HttpServletRequest request, HttpServletResponse response,
                        Long targetUserId, String pathPrefix) throws IOException {
        Long userId = targetUserId;
        String[] containerInfo;
        try {
            containerInfo = managerClient.getContainerInfo(userId);
        } catch (Exception e) {
            log.warn("No container for user {}: {}", userId, e.getMessage());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Container not available");
            return;
        }
        String address = containerInfo[0];
        String gatewayToken = containerInfo[1];

        String path = request.getRequestURI().replaceFirst("^" + pathPrefix, "");
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
                    // Drop CSP entirely: we need iframe embedding + inline script injection
                    return;
                }
                values.forEach(v -> response.addHeader(name, v));
            });

            // Stream response body; inject hide-UI script into HTML pages
            try (InputStream body = upstreamResponse.body()) {
                String ct = upstreamResponse.headers().firstValue("content-type").orElse("");
                if (ct.startsWith("text/html")) {
                    byte[] bytes = body.readAllBytes();
                    String html = new String(bytes, StandardCharsets.UTF_8);
                    // Only hide topbar/nav for regular user proxy (/app), not admin proxy
                    if ("/app".equals(pathPrefix)) {
                        html = injectHideScript(html);
                    }
                    // Clear localStorage for admin-proxy to prevent cross-container cache pollution
                    if (pathPrefix.startsWith("/admin-proxy/")) {
                        html = injectClearStorageScript(html);
                    }
                    byte[] out = html.getBytes(StandardCharsets.UTF_8);
                    response.setContentLength(out.length);
                    response.getOutputStream().write(out);
                } else {
                    body.transferTo(response.getOutputStream());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy interrupted");
        } catch (Exception e) {
            log.error("Proxy error for user {} -> {}: {}", userId, targetUrl, e.toString(), e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e);
        }
    }

    /**
     * Injects a script that clears localStorage/sessionStorage before the app loads,
     * preventing stale cache from a previously viewed container from showing up.
     */
    private static String injectClearStorageScript(String html) {
        String script = """
            <script data-portal-clear="1">
            (function(){
              try { localStorage.clear(); sessionStorage.clear(); } catch(_) {}
            })();
            </script>
            """;
        if (html.contains("<head>")) {
            return html.replace("<head>", "<head>" + script);
        }
        return script + html;
    }

    /**
     * Injects a script into openclaw's HTML to hide the topbar and nav sidebar.
     * Runs before Lit initialises so classes are locked from the start.
     */
    private static String injectHideScript(String html) {
        // NOTE: openclaw-app renders into LIGHT DOM (no shadow root).
        // CSS and shell element are in the regular document, not shadow DOM.
        String script = """
            <script data-portal-hide="1">
            (function(){
              var FORCE = ['shell--onboarding','shell--chat-focus'];
              var CSS = '.topbar{display:none!important}.nav{display:none!important;width:0!important;overflow:hidden!important}.shell{grid-template-rows:0 1fr!important;grid-template-columns:0 1fr!important}';

              // Inject CSS into document head (light DOM, not shadow DOM)
              if(!document.querySelector('[data-ph]')){
                var s = document.createElement('style');
                s.setAttribute('data-ph','1');
                s.textContent = CSS;
                document.head.appendChild(s);
              }

              function lockShell(shell){
                if(shell._ph) return;
                shell._ph = true;
                FORCE.forEach(function(c){ shell.classList.add(c); });
                // Intercept setAttribute so Lit re-renders can't remove our classes
                var orig = shell.setAttribute.bind(shell);
                shell.setAttribute = function(name, value){
                  if(name === 'class'){
                    var set = new Set(value.trim().split(/\\s+/).filter(Boolean));
                    FORCE.forEach(function(c){ set.add(c); });
                    value = Array.from(set).join(' ');
                  }
                  orig(name, value);
                };
              }

              // Shell is in LIGHT DOM - poll document directly (no shadow root needed)
              var t = setInterval(function(){
                var shell = document.querySelector('.shell');
                if(shell){ lockShell(shell); clearInterval(t); }
              }, 50);
              setTimeout(function(){ clearInterval(t); }, 15000);
            })();
            </script>
            """;
        if (html.contains("</head>")) {
            return html.replace("</head>", script + "</head>");
        }
        return script + html;
    }
}

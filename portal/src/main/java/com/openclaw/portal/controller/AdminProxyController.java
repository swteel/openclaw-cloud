package com.openclaw.portal.controller;

import com.openclaw.portal.handler.ProxyHandler;
import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Proxies /admin-proxy/{uid}/** to the target user's openclaw container.
 * Only accessible to ADMIN role (enforced by AuthFilter + role check here).
 *
 * On first entry (no ?token= in URL), redirects to /admin-proxy/{uid}/chat?token={gatewayToken}
 * so the OpenClaw SPA can authenticate. This also pairs with localStorage.clear() injection
 * to prevent stale cross-container cache.
 */
@RestController
public class AdminProxyController {

    private final ProxyHandler proxyHandler;
    private final ManagerClient managerClient;

    public AdminProxyController(ProxyHandler proxyHandler, ManagerClient managerClient) {
        this.proxyHandler = proxyHandler;
        this.managerClient = managerClient;
    }

    @RequestMapping("/admin-proxy/{uid}/**")
    public void adminProxy(@PathVariable("uid") Long uid,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin only");
            return;
        }

        // Only redirect full HTML navigation requests (not assets, not XHR/API calls).
        // Browser navigations have Accept: text/html; assets and XHR calls use other Accept values.
        String query = request.getQueryString();
        String accept = request.getHeader("Accept");
        boolean isHtmlNavigation = accept != null && accept.contains("text/html");
        if (isHtmlNavigation && (query == null || !query.contains("token="))) {
            String[] containerInfo;
            try {
                containerInfo = managerClient.getContainerInfo(uid);
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Container not available");
                return;
            }
            String gatewayToken = containerInfo[1];
            String encodedToken = URLEncoder.encode(gatewayToken, StandardCharsets.UTF_8);
            response.sendRedirect("/admin-proxy/" + uid + "/chat?token=" + encodedToken);
            return;
        }

        proxyHandler.proxyTo(request, response, uid, "/admin-proxy/" + uid);
    }
}

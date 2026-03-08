package com.openclaw.portal.controller;

import com.openclaw.portal.handler.ProxyHandler;
import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AppProxyController {

    // Paths in openclaw that expose sensitive config (API keys, provider settings, etc.)
    private static final String[] BLOCKED_PATH_SEGMENTS = {
            "/settings", "/providers", "/models/config"
    };

    private final ProxyHandler proxyHandler;
    private final ManagerClient managerClient;

    public AppProxyController(ProxyHandler proxyHandler, ManagerClient managerClient) {
        this.proxyHandler = proxyHandler;
        this.managerClient = managerClient;
    }

    @RequestMapping("/app-c/{containerName}/**")
    public void proxyByContainerName(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        // Extract containerName from path: /app-c/{containerName}/...
        String afterPrefix = uri.replaceFirst("^/app-c/", "");
        int slash = afterPrefix.indexOf('/');
        String containerName = slash >= 0 ? afterPrefix.substring(0, slash) : afterPrefix;
        String pathPrefix = "/app-c/" + containerName;

        String[] containerInfo;
        try {
            containerInfo = managerClient.getContainerInfoByName(containerName);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Container not available");
            return;
        }
        proxyHandler.proxyToByAddress(request, response, containerInfo[0], containerInfo[1], pathPrefix);
    }

    @RequestMapping("/app/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            String path = request.getRequestURI().replaceFirst("^/app", "");
            for (String blocked : BLOCKED_PATH_SEGMENTS) {
                if (path.startsWith(blocked)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                    return;
                }
            }
        }
        proxyHandler.proxy(request, response);
    }
}

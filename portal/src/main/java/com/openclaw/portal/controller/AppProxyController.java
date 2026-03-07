package com.openclaw.portal.controller;

import com.openclaw.portal.handler.ProxyHandler;
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

    public AppProxyController(ProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler;
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

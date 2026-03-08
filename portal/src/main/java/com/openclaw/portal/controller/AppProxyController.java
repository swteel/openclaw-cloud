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
        proxyHandler.proxy(request, response);
    }
}

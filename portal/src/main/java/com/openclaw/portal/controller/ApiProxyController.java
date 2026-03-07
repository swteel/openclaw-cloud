package com.openclaw.portal.controller;

import com.openclaw.portal.service.ManagerClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Proxies /api/admin/** and /api/containers/** to the manager service,
 * forwarding the user's JWT token via Authorization header.
 */
@RestController
public class ApiProxyController {

    private final ManagerClient managerClient;

    public ApiProxyController(ManagerClient managerClient) {
        this.managerClient = managerClient;
    }

    @RequestMapping(value = {"/api/admin/**", "/api/containers/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<Object> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) Object body) {
        String token = (String) request.getAttribute("token");
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = query != null ? path + "?" + query : path;
        String method = request.getMethod();

        return managerClient.proxyRequest(method, fullPath, token, body);
    }
}

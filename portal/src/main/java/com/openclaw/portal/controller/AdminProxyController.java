package com.openclaw.portal.controller;

import com.openclaw.portal.handler.ProxyHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Proxies /admin-proxy/{uid}/** to the target user's openclaw container.
 * Only accessible to ADMIN role (enforced by AuthFilter + role check here).
 */
@RestController
public class AdminProxyController {

    private final ProxyHandler proxyHandler;

    public AdminProxyController(ProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler;
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
        proxyHandler.proxyTo(request, response, uid, "/admin-proxy/" + uid);
    }
}

package com.openclaw.portal.controller;

import com.openclaw.portal.handler.ProxyHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AppProxyController {

    private final ProxyHandler proxyHandler;

    public AppProxyController(ProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler;
    }

    @RequestMapping("/app/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        proxyHandler.proxy(request, response);
    }
}

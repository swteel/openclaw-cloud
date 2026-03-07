package com.openclaw.portal.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpaController {

    private static final Resource ADMIN_INDEX = new ClassPathResource("static/admin/index.html");
    private static final Resource USER_INDEX  = new ClassPathResource("static/index.html");

    /** Admin SPA root — /admin and /admin/ */
    @GetMapping(value = {"/admin", "/admin/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> adminRoot() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(ADMIN_INDEX);
    }
}

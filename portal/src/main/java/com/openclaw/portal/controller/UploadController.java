package com.openclaw.portal.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

@RestController
@RequestMapping("/portal/upload")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    @Value("${portal.workspace-base:/workspace}")
    private String workspaceBase;

    @PostMapping("/{*filePath}")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable("filePath") String filePath,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // Strip leading slash and sanitize path to prevent directory traversal
        String stripped = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String sanitized = Paths.get(stripped).normalize().toString();
        if (sanitized.startsWith("..") || sanitized.contains("/../")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid path"));
        }

        Path userWorkspace = Paths.get(workspaceBase, "user-" + userId);
        Path targetPath = userWorkspace.resolve(sanitized).normalize();

        // Ensure target is within user workspace
        if (!targetPath.startsWith(userWorkspace)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Path escape not allowed"));
        }

        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("User {} uploaded file to {}", userId, targetPath);
            return ResponseEntity.ok(Map.of("success", true, "path", sanitized));
        } catch (IOException e) {
            log.error("Upload failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
    }
}

package com.openclaw.portal.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/portal/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Value("${portal.workspace-base:/workspace}")
    private String workspaceBase;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFiles(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Path userWorkspace = Paths.get(workspaceBase, "user-" + userId);

        if (!Files.exists(userWorkspace)) {
            return ResponseEntity.ok(Map.of("success", true, "files", List.of()));
        }

        try (Stream<Path> walk = Files.walk(userWorkspace)) {
            List<String> files = walk
                    .filter(Files::isRegularFile)
                    .map(p -> userWorkspace.relativize(p).toString())
                    .sorted()
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "files", files));
        } catch (IOException e) {
            log.error("Failed to list files for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Failed to list files"));
        }
    }

    @DeleteMapping("/{*filePath}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable("filePath") String filePath,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        String stripped = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String sanitized = Paths.get(stripped).normalize().toString();
        if (sanitized.startsWith("..") || sanitized.contains("/../")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid path"));
        }

        Path userWorkspace = Paths.get(workspaceBase, "user-" + userId);
        Path targetPath = userWorkspace.resolve(sanitized).normalize();

        if (!targetPath.startsWith(userWorkspace)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Path escape not allowed"));
        }

        try {
            boolean deleted = Files.deleteIfExists(targetPath);
            if (!deleted) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "File not found"));
            }
            log.info("User {} deleted file {}", userId, targetPath);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IOException e) {
            log.error("Failed to delete file for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Delete failed: " + e.getMessage()));
        }
    }
}

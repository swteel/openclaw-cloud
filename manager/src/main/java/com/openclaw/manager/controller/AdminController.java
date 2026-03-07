package com.openclaw.manager.controller;

import com.openclaw.manager.config.PlatformProperties;
import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.domain.repository.UserRepository;
import com.openclaw.manager.dto.ApiResponse;
import com.openclaw.manager.dto.ContainerInfo;
import com.openclaw.manager.service.ContainerLifecycleService;
import com.openclaw.manager.service.PlatformConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ContainerLifecycleService containerService;
    private final ContainerRepository containerRepo;
    private final UserRepository userRepo;
    private final PlatformConfigService configService;
    private final PlatformProperties props;

    public AdminController(ContainerLifecycleService containerService, ContainerRepository containerRepo,
                           UserRepository userRepo, PlatformConfigService configService, PlatformProperties props) {
        this.containerService = containerService;
        this.containerRepo = containerRepo;
        this.userRepo = userRepo;
        this.configService = configService;
        this.props = props;
    }

    @GetMapping("/containers")
    public ResponseEntity<ApiResponse<List<ContainerInfo>>> listContainers() {
        return ResponseEntity.ok(ApiResponse.ok(containerService.getAllContainers()));
    }

    @PostMapping("/containers/{uid}/stop")
    public ResponseEntity<ApiResponse<Void>> stopContainer(@PathVariable("uid") Long uid) {
        Container container = containerRepo.findByUserId(uid)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + uid));
        containerService.stopContainer(container);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/containers/{uid}/remove")
    public ResponseEntity<ApiResponse<Void>> removeContainer(@PathVariable("uid") Long uid) {
        Container container = containerRepo.findByUserId(uid)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + uid));
        containerService.removeContainerKeepVolume(container);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long running = containerRepo.countByStatus("RUNNING");
        long stopped = containerRepo.countByStatus("STOPPED");
        long total = containerRepo.count();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "total", total,
                "running", running,
                "stopped", stopped
        )));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listUsers() {
        List<User> users = userRepo.findAll();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("createdAt", u.getCreatedAt());
            m.put("lastActiveAt", u.getLastActiveAt());
            containerRepo.findByUserId(u.getId()).ifPresent(c -> m.put("containerStatus", c.getStatus()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/users/{uid}/role")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable("uid") Long uid,
            @RequestBody Map<String, String> body) {
        User user = userRepo.findById(uid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + uid));
        String role = body.get("role");
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid role"));
        }
        user.setRole(role);
        userRepo.save(user);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        String apiKey = configService.get("dashscope_api_key");
        config.put("dashscopeApiKey", apiKey != null ? "****" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "");
        config.put("maxContainers", props.getMaxContainers());
        config.put("portRange", props.getPortRangeStart() + "-" + props.getPortRangeEnd());
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<Void>> updateConfig(@RequestBody Map<String, String> body) {
        if (body.containsKey("dashscopeApiKey") && !body.get("dashscopeApiKey").isBlank()) {
            configService.set("dashscope_api_key", body.get("dashscopeApiKey"));
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}

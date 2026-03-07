package com.openclaw.manager.controller;

import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.dto.ApiResponse;
import com.openclaw.manager.dto.ContainerInfo;
import com.openclaw.manager.service.ContainerLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ContainerLifecycleService containerService;
    private final ContainerRepository containerRepo;

    public AdminController(ContainerLifecycleService containerService, ContainerRepository containerRepo) {
        this.containerService = containerService;
        this.containerRepo = containerRepo;
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
}

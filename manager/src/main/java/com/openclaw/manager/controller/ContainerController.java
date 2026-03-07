package com.openclaw.manager.controller;

import com.openclaw.manager.domain.repository.UserRepository;
import com.openclaw.manager.dto.ApiResponse;
import com.openclaw.manager.dto.ContainerInfo;
import com.openclaw.manager.service.ContainerLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerLifecycleService containerService;
    private final UserRepository userRepository;

    public ContainerController(ContainerLifecycleService containerService, UserRepository userRepository) {
        this.containerService = containerService;
        this.userRepository = userRepository;
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<ContainerInfo>> getMyContainer(@AuthenticationPrincipal Long userId) {
        ContainerInfo info = containerService.getContainerInfo(userId);
        userRepository.findById(userId).ifPresent(u -> info.setGatewayToken(u.getGatewayToken()));
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @PostMapping("/my/start")
    public ResponseEntity<ApiResponse<ContainerInfo>> startMyContainer(@AuthenticationPrincipal Long userId) {
        ContainerInfo info = containerService.startContainer(userId);
        return ResponseEntity.ok(ApiResponse.ok("Container started", info));
    }

    @GetMapping("/my/connection")
    public ResponseEntity<ApiResponse<Map<String, String>>> getConnectionCmd(@AuthenticationPrincipal Long userId) {
        String cmd = containerService.getWindowsNodeConnectionCmd(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("command", cmd)));
    }

    @PutMapping("/my/browser-mode")
    public ResponseEntity<ApiResponse<ContainerInfo>> updateBrowserMode(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        if (!"BUILT_IN".equals(mode) && !"WINDOWS_NODE".equals(mode)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid mode: " + mode));
        }
        ContainerInfo info = containerService.updateBrowserMode(userId, mode);
        return ResponseEntity.ok(ApiResponse.ok("Browser mode updated", info));
    }
}

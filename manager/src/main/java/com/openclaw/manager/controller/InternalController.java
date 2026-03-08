package com.openclaw.manager.controller;

import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.dto.ApiResponse;
import com.openclaw.manager.service.AuthService;
import com.openclaw.manager.service.ContainerLifecycleService;
import com.openclaw.manager.service.UserActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal")
@PreAuthorize("hasRole('INTERNAL')")
public class InternalController {

    private final AuthService authService;
    private final ContainerLifecycleService containerService;
    private final UserActivityService activityService;

    public InternalController(AuthService authService, ContainerLifecycleService containerService,
                               UserActivityService activityService) {
        this.authService = authService;
        this.containerService = containerService;
        this.activityService = activityService;
    }

    @PostMapping("/auth/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        User user = authService.verifyToken(token);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        )));
    }

    @GetMapping("/containers/{uid}/address")
    public ResponseEntity<ApiResponse<Map<String, String>>> getContainerAddress(@PathVariable("uid") Long uid) {
        String address = containerService.getContainerAddress(uid);
        String gatewayToken = authService.getGatewayToken(uid);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("address", address, "gatewayToken", gatewayToken)));
    }

    @GetMapping("/containers/by-name/{name}/address")
    public ResponseEntity<ApiResponse<Map<String, String>>> getContainerAddressByName(@PathVariable("name") String name) {
        String address = containerService.getContainerAddressByName(name);
        Long userId = containerService.getUserIdByContainerName(name);
        String gatewayToken = authService.getGatewayToken(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("address", address, "gatewayToken", gatewayToken)));
    }

    @PostMapping("/users/{uid}/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(@PathVariable("uid") Long uid) {
        activityService.recordHeartbeat(uid);
        containerService.wakeIfNeeded(uid);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}

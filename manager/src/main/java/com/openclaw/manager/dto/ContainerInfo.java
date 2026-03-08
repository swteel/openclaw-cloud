package com.openclaw.manager.dto;

import java.time.LocalDateTime;

public class ContainerInfo {
    private Long id;
    private Long userId;
    private String containerName;
    private int hostPort;
    private String status;
    private String browserMode;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private String gatewayToken;

    public ContainerInfo() {}

    public ContainerInfo(Long id, Long userId, String containerName, int hostPort, String status,
                         String browserMode, LocalDateTime createdAt, LocalDateTime startedAt) {
        this.id = id;
        this.userId = userId;
        this.containerName = containerName;
        this.hostPort = hostPort;
        this.status = status;
        this.browserMode = browserMode;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }

    public int getHostPort() { return hostPort; }
    public void setHostPort(int hostPort) { this.hostPort = hostPort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBrowserMode() { return browserMode; }
    public void setBrowserMode(String browserMode) { this.browserMode = browserMode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public String getGatewayToken() { return gatewayToken; }
    public void setGatewayToken(String gatewayToken) { this.gatewayToken = gatewayToken; }
}

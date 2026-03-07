package com.openclaw.manager.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "containers")
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "container_id", nullable = false, length = 128)
    private String containerId;

    @Column(name = "container_name", nullable = false, length = 128)
    private String containerName;

    @Column(name = "host_port", nullable = false, unique = true)
    private int hostPort;

    @Column(nullable = false, length = 16)
    private String status = "RUNNING";

    @Column(name = "browser_mode", nullable = false, length = 16)
    private String browserMode = "BUILT_IN";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

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
}

package com.openclaw.manager.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "port_allocations")
public class PortAllocation {

    @Id
    private int port;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }
}

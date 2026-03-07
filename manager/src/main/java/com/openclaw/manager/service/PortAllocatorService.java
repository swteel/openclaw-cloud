package com.openclaw.manager.service;

import com.openclaw.manager.config.PlatformProperties;
import com.openclaw.manager.domain.entity.PortAllocation;
import com.openclaw.manager.domain.repository.PortAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PortAllocatorService {

    private final PortAllocationRepository repo;
    private final PlatformProperties props;

    public PortAllocatorService(PortAllocationRepository repo, PlatformProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional
    public int allocate(Long userId) {
        List<Integer> usedPorts = repo.findAllAllocatedPorts();
        Set<Integer> usedSet = new HashSet<>(usedPorts);

        for (int port = props.getPortRangeStart(); port <= props.getPortRangeEnd(); port++) {
            if (!usedSet.contains(port)) {
                PortAllocation alloc = new PortAllocation();
                alloc.setPort(port);
                alloc.setUserId(userId);
                repo.save(alloc);
                return port;
            }
        }
        throw new IllegalStateException("No available ports in range " +
                props.getPortRangeStart() + "-" + props.getPortRangeEnd());
    }

    @Transactional
    public void release(Long userId) {
        repo.deleteByUserId(userId);
    }
}

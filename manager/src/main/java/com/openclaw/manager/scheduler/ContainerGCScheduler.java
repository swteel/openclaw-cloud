package com.openclaw.manager.scheduler;

import com.github.dockerjava.api.DockerClient;
import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.service.ContainerLifecycleService;
import com.openclaw.manager.service.PortAllocatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ContainerGCScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContainerGCScheduler.class);

    private final ContainerRepository containerRepo;
    private final ContainerLifecycleService lifecycleService;
    private final DockerClient docker;
    private final PortAllocatorService portAllocator;

    public ContainerGCScheduler(ContainerRepository containerRepo,
                                 ContainerLifecycleService lifecycleService,
                                 DockerClient docker,
                                 PortAllocatorService portAllocator) {
        this.containerRepo = containerRepo;
        this.lifecycleService = lifecycleService;
        this.docker = docker;
        this.portAllocator = portAllocator;
    }

    /**
     * Every 2 minutes: sync DB status with actual Docker container state.
     * Fixes records that were manually removed or crashed outside of Manager's control.
     */
    @Scheduled(fixedDelay = 120_000)
    public void syncDockerState() {
        try {
            doSync();
        } catch (Exception e) {
            log.error("Docker state sync failed: {}", e.getMessage(), e);
        }
    }

    private void doSync() {
        List<Container> active = containerRepo.findAll().stream()
                .filter(c -> "RUNNING".equals(c.getStatus()) || "STOPPED".equals(c.getStatus()) || "REMOVING".equals(c.getStatus()))
                .toList();

        log.info("Docker state sync: checking {} active containers", active.size());
        int synced = 0;
        for (Container c : active) {
            try {
                com.github.dockerjava.api.model.Container[] found = docker.listContainersCmd()
                        .withShowAll(true)
                        .withIdFilter(List.of(c.getContainerId()))
                        .exec()
                        .toArray(new com.github.dockerjava.api.model.Container[0]);

                if (found.length == 0) {
                    // Container gone from Docker entirely - delete the DB record
                    log.warn("Container {} not found in Docker, deleting record", c.getContainerName());
                    portAllocator.releasePort(c.getHostPort());
                    containerRepo.delete(c);
                    synced++;
                } else {
                    String dockerStatus = found[0].getState(); // "running", "exited", "paused", etc.
                    if ("running".equals(dockerStatus) && "STOPPED".equals(c.getStatus())) {
                        c.setStatus("RUNNING");
                        containerRepo.save(c);
                        synced++;
                        log.info("Synced {} STOPPED -> RUNNING", c.getContainerName());
                    } else if (!"running".equals(dockerStatus) && "RUNNING".equals(c.getStatus())) {
                        c.setStatus("STOPPED");
                        containerRepo.save(c);
                        synced++;
                        log.info("Synced {} RUNNING -> STOPPED (docker state: {})",
                                c.getContainerName(), dockerStatus);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to sync state for container {}: {}", c.getContainerName(), e.getMessage());
            }
        }

        if (synced > 0) {
            log.info("Docker state sync complete: {} records updated", synced);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void gc() {
        log.info("Running container GC...");

        LocalDateTime stopThreshold = LocalDateTime.now().minusHours(24);
        List<Container> toStop = containerRepo.findRunningInactiveContainers(stopThreshold);
        log.info("Stopping {} inactive containers (last active > 24h)", toStop.size());
        toStop.forEach(c -> {
            try {
                lifecycleService.stopContainer(c);
            } catch (Exception e) {
                log.error("Failed to stop container {}: {}", c.getContainerId(), e.getMessage());
            }
        });

        LocalDateTime removeThreshold = LocalDateTime.now().minusDays(30);
        List<Container> toRemove = containerRepo.findStoppedInactiveContainers(removeThreshold);
        log.info("Removing {} long-inactive containers (last active > 30d)", toRemove.size());
        toRemove.forEach(c -> {
            try {
                lifecycleService.removeContainerKeepVolume(c);
            } catch (Exception e) {
                log.error("Failed to remove container {}: {}", c.getContainerId(), e.getMessage());
            }
        });

        log.info("Container GC complete. Stopped: {}, Removed: {}", toStop.size(), toRemove.size());
    }
}

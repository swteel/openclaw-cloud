package com.openclaw.manager.scheduler;

import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.service.ContainerLifecycleService;
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

    public ContainerGCScheduler(ContainerRepository containerRepo,
                                 ContainerLifecycleService lifecycleService) {
        this.containerRepo = containerRepo;
        this.lifecycleService = lifecycleService;
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

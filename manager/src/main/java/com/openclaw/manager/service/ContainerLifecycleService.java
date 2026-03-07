package com.openclaw.manager.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.openclaw.manager.config.PlatformProperties;
import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.dto.ContainerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContainerLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ContainerLifecycleService.class);
    private static final int OPENCLAW_PORT = 18789;

    private final DockerClient docker;
    private final ContainerRepository containerRepo;
    private final PortAllocatorService portAllocator;
    private final PlatformProperties props;

    public ContainerLifecycleService(DockerClient docker, ContainerRepository containerRepo,
                                     PortAllocatorService portAllocator, PlatformProperties props) {
        this.docker = docker;
        this.containerRepo = containerRepo;
        this.portAllocator = portAllocator;
        this.props = props;
    }

    @Transactional
    public ContainerInfo createAndStart(User user) {
        long runningCount = containerRepo.countByStatus("RUNNING");
        if (runningCount >= props.getMaxContainers()) {
            throw new IllegalStateException("Maximum container limit reached: " + props.getMaxContainers());
        }

        int hostPort = portAllocator.allocate(user.getId());
        String containerName = "openclaw-user-" + user.getId();
        String volumeName = "openclaw-data-" + user.getId();

        ExposedPort exposedPort = ExposedPort.tcp(OPENCLAW_PORT);
        Ports portBindings = new Ports();
        portBindings.bind(exposedPort, Ports.Binding.bindPort(hostPort));

        Volume dataVolume = new Volume("/root/.openclaw");

        CreateContainerResponse created = docker.createContainerCmd(props.getContainerImage())
                .withName(containerName)
                .withExposedPorts(exposedPort)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withBinds(new Bind(volumeName, dataVolume))
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .withEnv(
                        "OPENCLAW_GATEWAY_TOKEN=" + user.getGatewayToken(),
                        "DASHSCOPE_API_KEY=" + props.getDashscopeApiKey()
                )
                .exec();

        docker.startContainerCmd(created.getId()).exec();

        // Connect to platform network so portal can reach it by container name
        try {
            docker.connectToNetworkCmd()
                    .withNetworkId(props.getPlatformNetwork())
                    .withContainerId(created.getId())
                    .exec();
        } catch (Exception e) {
            log.warn("Could not connect container {} to network {}: {}", containerName, props.getPlatformNetwork(), e.getMessage());
        }

        Container container = new Container();
        container.setUserId(user.getId());
        container.setContainerId(created.getId());
        container.setContainerName(containerName);
        container.setHostPort(hostPort);
        container.setStatus("RUNNING");
        container.setStartedAt(LocalDateTime.now());
        containerRepo.save(container);

        log.info("Created and started container {} for user {}", containerName, user.getId());
        return toInfo(container);
    }

    @Transactional
    public void stopContainer(Container container) {
        try {
            docker.stopContainerCmd(container.getContainerId()).exec();
        } catch (Exception e) {
            log.warn("Error stopping container {}: {}", container.getContainerId(), e.getMessage());
        }
        container.setStatus("STOPPED");
        containerRepo.save(container);
        log.info("Stopped container {} for user {}", container.getContainerName(), container.getUserId());
    }

    @Transactional
    public void removeContainerKeepVolume(Container container) {
        try {
            docker.removeContainerCmd(container.getContainerId())
                    .withForce(true)
                    .exec();
        } catch (Exception e) {
            log.warn("Error removing container {}: {}", container.getContainerId(), e.getMessage());
        }
        portAllocator.release(container.getUserId());
        container.setStatus("REMOVING");
        containerRepo.save(container);
        log.info("Removed container {} for user {}", container.getContainerName(), container.getUserId());
    }

    @Transactional
    public void wakeIfNeeded(Long userId) {
        Container container = containerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));

        if ("STOPPED".equals(container.getStatus())) {
            try {
                docker.startContainerCmd(container.getContainerId()).exec();
                container.setStatus("RUNNING");
                container.setStartedAt(LocalDateTime.now());
                containerRepo.save(container);
                log.info("Woke container {} for user {}", container.getContainerName(), userId);
            } catch (Exception e) {
                log.error("Failed to wake container for user {}: {}", userId, e.getMessage());
                throw new RuntimeException("Failed to start container", e);
            }
        }
    }

    @Transactional
    public ContainerInfo startContainer(Long userId) {
        Container container = containerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));

        if (!"STOPPED".equals(container.getStatus())) {
            return toInfo(container);
        }

        docker.startContainerCmd(container.getContainerId()).exec();
        container.setStatus("RUNNING");
        container.setStartedAt(LocalDateTime.now());
        containerRepo.save(container);
        return toInfo(container);
    }

    public ContainerInfo getContainerInfo(Long userId) {
        Container container = containerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        return toInfo(container);
    }

    public String getContainerAddress(Long userId) {
        Container container = containerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        // Use container name on shared network so portal can reach it directly
        return container.getContainerName() + ":" + OPENCLAW_PORT;
    }

    @Transactional
    public ContainerInfo updateBrowserMode(Long userId, String mode) {
        Container container = containerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        container.setBrowserMode(mode);
        containerRepo.save(container);
        return toInfo(container);
    }

    public String getWindowsNodeConnectionCmd(Long userId) {
        Container container = containerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        return "openclaw browser-node --server ws://HOST:" + container.getHostPort();
    }

    public List<ContainerInfo> getAllContainers() {
        return containerRepo.findAll().stream().map(this::toInfo).toList();
    }

    private ContainerInfo toInfo(Container c) {
        return new ContainerInfo(
                c.getUserId(), c.getContainerName(), c.getHostPort(),
                c.getStatus(), c.getBrowserMode(), c.getCreatedAt(), c.getStartedAt()
        );
    }
}

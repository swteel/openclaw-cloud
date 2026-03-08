package com.openclaw.manager.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.openclaw.manager.config.PlatformProperties;
import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.entity.User;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.dto.ContainerInfo;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ContainerLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ContainerLifecycleService.class);
    private static final int OPENCLAW_PORT = 18789;

    private final DockerClient docker;
    private final ContainerRepository containerRepo;
    private final PortAllocatorService portAllocator;
    private final PlatformProperties props;
    private final PlatformConfigService configService;

    public ContainerLifecycleService(DockerClient docker, ContainerRepository containerRepo,
                                     PortAllocatorService portAllocator, PlatformProperties props,
                                     PlatformConfigService configService) {
        this.docker = docker;
        this.containerRepo = containerRepo;
        this.portAllocator = portAllocator;
        this.props = props;
        this.configService = configService;
    }

    @Transactional
    public ContainerInfo createAndStart(User user) {
        long runningCount = containerRepo.countByStatus("RUNNING");
        if (runningCount >= props.getMaxContainers()) {
            throw new IllegalStateException("Maximum container limit reached: " + props.getMaxContainers());
        }

        int hostPort = portAllocator.allocate(user.getId());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String containerName = "openclaw-" + user.getId() + "-" + suffix;
        String volumeName = "openclaw-data-" + user.getId() + "-" + suffix;

        ExposedPort exposedPort = ExposedPort.tcp(OPENCLAW_PORT);
        Ports portBindings = new Ports();
        portBindings.bind(exposedPort, Ports.Binding.bindPort(hostPort));

        Volume dataVolume = new Volume("/root/.openclaw");

        // Remove any stale container with the same name (e.g. from a previous failed attempt)
        try {
            docker.listContainersCmd().withShowAll(true).withNameFilter(List.of(containerName)).exec()
                    .stream().filter(c -> {
                        for (String n : c.getNames()) {
                            if (n.equals("/" + containerName) || n.equals(containerName)) return true;
                        }
                        return false;
                    })
                    .forEach(c -> {
                        log.warn("Removing stale container {} before recreating", containerName);
                        try { docker.removeContainerCmd(c.getId()).withForce(true).exec(); } catch (Exception ex) {
                            log.warn("Failed to remove stale container {}: {}", containerName, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Stale container check failed: {}", e.getMessage());
        }

        CreateContainerResponse created = docker.createContainerCmd(props.getContainerImage())
                .withName(containerName)
                .withExposedPorts(exposedPort)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withBinds(new Bind(volumeName, dataVolume))
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .withEnv(
                        "OPENCLAW_GATEWAY_TOKEN=" + user.getGatewayToken(),
                        "DASHSCOPE_API_KEY=" + configService.get("dashscope_api_key", props.getDashscopeApiKey())
                )
                .exec();

        injectOpenclawConfig(created.getId());
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
        portAllocator.releasePort(container.getHostPort());
        containerRepo.delete(container);
        log.info("Removed container {} for user {}", container.getContainerName(), container.getUserId());
    }

    @Transactional
    public void wakeIfNeeded(Long userId) {
        Container container = containerRepo.findFirstByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));

        if ("STOPPED".equals(container.getStatus())) {
            try {
                injectOpenclawConfig(container.getContainerId());
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
        Container container = containerRepo.findFirstByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        return startContainerEntity(container);
    }

    @Transactional
    public ContainerInfo startContainerEntity(Container container) {
        if (!"STOPPED".equals(container.getStatus())) {
            return toInfo(container);
        }

        injectOpenclawConfig(container.getContainerId());
        docker.startContainerCmd(container.getContainerId()).exec();
        container.setStatus("RUNNING");
        container.setStartedAt(LocalDateTime.now());
        containerRepo.save(container);
        return toInfo(container);
    }

    public ContainerInfo getContainerInfo(Long userId) {
        Container container = containerRepo.findFirstByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        return toInfo(container);
    }

    public String getContainerAddress(Long userId) {
        Container container = containerRepo.findFirstByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        // Use container name on shared network so portal can reach it directly
        return container.getContainerName() + ":" + OPENCLAW_PORT;
    }

    public String getContainerAddressByName(String containerName) {
        Container container = containerRepo.findByContainerName(containerName)
                .orElseThrow(() -> new IllegalArgumentException("No container with name " + containerName));
        return container.getContainerName() + ":" + OPENCLAW_PORT;
    }

    public Long getUserIdByContainerName(String containerName) {
        Container container = containerRepo.findByContainerName(containerName)
                .orElseThrow(() -> new IllegalArgumentException("No container with name " + containerName));
        return container.getUserId();
    }

    @Transactional
    public ContainerInfo updateBrowserMode(Long userId, String mode) {
        Container container = containerRepo.findFirstByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        container.setBrowserMode(mode);
        containerRepo.save(container);
        return toInfo(container);
    }

    public String getWindowsNodeConnectionCmd(Long userId) {
        Container container = containerRepo.findFirstByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No container for user " + userId));
        return "openclaw browser-node --server ws://HOST:" + container.getHostPort();
    }

    public List<ContainerInfo> getAllContainers() {
        return containerRepo.findAll().stream().map(this::toInfo).toList();
    }

    public List<ContainerInfo> getAllContainersForUser(Long userId) {
        return containerRepo.findAllByUserId(userId).stream().map(this::toInfo).toList();
    }

    /**
     * Copies a custom openclaw config into the container that sets allowedOrigins: ["*"],
     * overriding the image's default config-template.json.
     * The gateway token placeholder is preserved for openclaw's own env-var substitution.
     */
    private void injectOpenclawConfig(String containerId) {
        String config = """
                {
                  "gateway": {
                    "mode": "local",
                    "auth": { "mode": "token", "token": "${OPENCLAW_GATEWAY_TOKEN}" },
                    "controlUi": { "allowedOrigins": ["*"] },
                    "trustedProxies": ["172.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16"]
                  },
                  "models": {
                    "providers": {
                      "qwen-portal": {
                        "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "apiKey": "${DASHSCOPE_API_KEY}",
                        "api": "openai-completions",
                        "models": [
                          {
                            "id": "coder-model", "name": "Qwen Coder", "reasoning": false,
                            "input": ["text"],
                            "cost": {"input": 0, "output": 0, "cacheRead": 0, "cacheWrite": 0},
                            "contextWindow": 128000, "maxTokens": 8192
                          },
                          {
                            "id": "vision-model", "name": "Qwen Vision", "reasoning": false,
                            "input": ["text", "image"],
                            "cost": {"input": 0, "output": 0, "cacheRead": 0, "cacheWrite": 0},
                            "contextWindow": 128000, "maxTokens": 8192
                          }
                        ]
                      }
                    }
                  },
                  "agents": {
                    "defaults": {
                      "model": {
                        "primary": "qwen-portal/coder-model",
                        "fallbacks": ["qwen-portal/vision-model"]
                      },
                      "models": {
                        "qwen-portal/coder-model": {"alias": "qwen"},
                        "qwen-portal/vision-model": {}
                      },
                      "compaction": {"mode": "safeguard"},
                      "maxConcurrent": 4,
                      "subagents": {"maxConcurrent": 8}
                    }
                  },
                  "tools": { "profile": "full" },
                  "browser": { "attachOnly": false },
                  "commands": {
                    "native": "auto", "nativeSkills": "auto",
                    "restart": true, "ownerDisplay": "raw"
                  }
                }
                """;
        try {
            byte[] configBytes = config.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
                TarArchiveEntry entry = new TarArchiveEntry("config-template.json");
                entry.setSize(configBytes.length);
                tar.putArchiveEntry(entry);
                tar.write(configBytes);
                tar.closeArchiveEntry();
            }
            docker.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(new ByteArrayInputStream(baos.toByteArray()))
                    .withRemotePath("/etc/openclaw/")
                    .exec();
            log.debug("Injected openclaw config into container {}", containerId);
        } catch (IOException e) {
            log.warn("Failed to inject openclaw config into {}: {}", containerId, e.getMessage());
        }
    }

    private ContainerInfo toInfo(Container c) {
        return new ContainerInfo(
                c.getId(), c.getUserId(), c.getContainerName(), c.getHostPort(),
                c.getStatus(), c.getBrowserMode(), c.getCreatedAt(), c.getStartedAt()
        );
    }
}

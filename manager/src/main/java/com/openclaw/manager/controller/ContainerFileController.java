package com.openclaw.manager.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.openclaw.manager.domain.entity.Container;
import com.openclaw.manager.domain.repository.ContainerRepository;
import com.openclaw.manager.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/internal/containers/{containerName}/files")
@PreAuthorize("hasRole('INTERNAL')")
public class ContainerFileController {

    private static final Logger log = LoggerFactory.getLogger(ContainerFileController.class);
    private static final String UPLOAD_DIR = "/root/uploads";

    private final DockerClient docker;
    private final ContainerRepository containerRepo;

    public ContainerFileController(DockerClient docker, ContainerRepository containerRepo) {
        this.docker = docker;
        this.containerRepo = containerRepo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listFiles(
            @PathVariable("containerName") String containerName,
            @RequestHeader("X-User-Id") Long userId) {
        Container container = findAndVerify(containerName, userId);

        execInContainer(container.getContainerId(), "mkdir", "-p", UPLOAD_DIR);

        String output = execInContainerOutput(container.getContainerId(),
                "find", UPLOAD_DIR, "-maxdepth", "1", "-type", "f");

        List<String> files = Arrays.stream(output.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .map(path -> path.startsWith(UPLOAD_DIR + "/")
                        ? path.substring(UPLOAD_DIR.length() + 1)
                        : Paths.get(path).getFileName().toString())
                .sorted()
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Void>> uploadFile(
            @PathVariable("containerName") String containerName,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        Container container = findAndVerify(containerName, userId);

        execInContainer(container.getContainerId(), "mkdir", "-p", UPLOAD_DIR);

        String safeName = Paths.get(file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "upload").getFileName().toString();
        byte[] fileBytes = file.getBytes();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            TarArchiveEntry entry = new TarArchiveEntry(safeName);
            entry.setSize(fileBytes.length);
            tar.putArchiveEntry(entry);
            tar.write(fileBytes);
            tar.closeArchiveEntry();
        }

        docker.copyArchiveToContainerCmd(container.getContainerId())
                .withTarInputStream(new ByteArrayInputStream(baos.toByteArray()))
                .withRemotePath(UPLOAD_DIR)
                .exec();

        log.info("Uploaded {} to container {} for user {}", safeName, containerName, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/download")
    public void downloadFile(
            @PathVariable("containerName") String containerName,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("filename") String filename,
            HttpServletResponse response) throws IOException {
        Container container = findAndVerify(containerName, userId);

        String safeName = Paths.get(filename).getFileName().toString();
        String filePath = UPLOAD_DIR + "/" + safeName;

        try (InputStream tarStream = docker.copyArchiveFromContainerCmd(
                container.getContainerId(), filePath).exec();
             TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    response.setContentType("application/octet-stream");
                    response.setHeader("Content-Disposition",
                            "attachment; filename=\"" + safeName + "\"");
                    response.setContentLengthLong(entry.getSize());
                    tar.transferTo(response.getOutputStream());
                    return;
                }
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
        } catch (Exception e) {
            log.warn("Failed to download {} from container {}: {}", filename, containerName, e.getMessage());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found in container");
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable("containerName") String containerName,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("filename") String filename) {
        Container container = findAndVerify(containerName, userId);
        String safeName = Paths.get(filename).getFileName().toString();
        execInContainer(container.getContainerId(), "rm", "-f", UPLOAD_DIR + "/" + safeName);
        log.info("Deleted {} from container {} for user {}", safeName, containerName, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private Container findAndVerify(String containerName, Long userId) {
        Container container = containerRepo.findByContainerName(containerName)
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerName));
        if (!container.getUserId().equals(userId)) {
            throw new SecurityException("Container does not belong to user " + userId);
        }
        return container;
    }

    private void execInContainer(String containerId, String... cmd) {
        try {
            ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            docker.execStartCmd(exec.getId())
                    .exec(new ResultCallback.Adapter<>())
                    .awaitCompletion(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("exec {} in {}: {}", String.join(" ", cmd), containerId, e.getMessage());
        }
    }

    private String execInContainerOutput(String containerId, String... cmd) {
        try {
            ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStdout(true)
                    .exec();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            docker.execStartCmd(exec.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            out.writeBytes(frame.getPayload());
                        }
                    })
                    .awaitCompletion(10, TimeUnit.SECONDS);
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("exec output {} in {}: {}", String.join(" ", cmd), containerId, e.getMessage());
            return "";
        }
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(403).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
    }
}

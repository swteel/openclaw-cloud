package com.openclaw.portal.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Proxies container file operations between the user-ui and Manager's internal API.
 * All endpoints are protected by AuthFilter via /portal/container-files/** path prefix.
 */
@RestController
@RequestMapping("/portal/container-files/{containerName}")
public class ContainerFileProxyController {

    private static final Logger log = LoggerFactory.getLogger(ContainerFileProxyController.class);

    private final String managerUrl;
    private final String internalToken;
    private final HttpClient httpClient;

    public ContainerFileProxyController(
            @Value("${portal.manager-url}") String managerUrl,
            @Value("${portal.internal-token}") String internalToken) {
        this.managerUrl = managerUrl;
        this.internalToken = internalToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @GetMapping
    public ResponseEntity<String> listFiles(@PathVariable("containerName") String containerName,
                                            HttpServletRequest request) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(managerUrl + "/internal/containers/"
                        + URLEncoder.encode(containerName, StandardCharsets.UTF_8) + "/files"))
                .header("X-Internal-Token", internalToken)
                .header("X-User-Id", userId.toString())
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return ResponseEntity.status(resp.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp.body());
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@PathVariable("containerName") String containerName,
                                             @RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) throws Exception {
        Long userId = (Long) request.getAttribute("userId");

        String boundary = "Boundary" + System.currentTimeMillis();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        byte[] fileBytes = file.getBytes();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        baos.write(head.getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(managerUrl + "/internal/containers/"
                        + URLEncoder.encode(containerName, StandardCharsets.UTF_8) + "/files/upload"))
                .header("X-Internal-Token", internalToken)
                .header("X-User-Id", userId.toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return ResponseEntity.status(resp.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp.body());
    }

    @GetMapping("/download")
    public void downloadFile(@PathVariable("containerName") String containerName,
                             @RequestParam("filename") String filename,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(managerUrl + "/internal/containers/"
                        + URLEncoder.encode(containerName, StandardCharsets.UTF_8)
                        + "/files/download?filename="
                        + URLEncoder.encode(filename, StandardCharsets.UTF_8)))
                .header("X-Internal-Token", internalToken)
                .header("X-User-Id", userId.toString())
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            response.sendError(resp.statusCode(), "Failed to download file");
            return;
        }
        resp.headers().firstValue("content-type")
                .ifPresent(response::setContentType);
        resp.headers().firstValue("content-disposition")
                .ifPresent(v -> response.setHeader("Content-Disposition", v));
        resp.headers().firstValue("content-length")
                .ifPresent(v -> response.setContentLengthLong(Long.parseLong(v)));
        try (InputStream body = resp.body()) {
            body.transferTo(response.getOutputStream());
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteFile(@PathVariable("containerName") String containerName,
                                             @RequestParam("filename") String filename,
                                             HttpServletRequest request) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(managerUrl + "/internal/containers/"
                        + URLEncoder.encode(containerName, StandardCharsets.UTF_8)
                        + "/files?filename="
                        + URLEncoder.encode(filename, StandardCharsets.UTF_8)))
                .header("X-Internal-Token", internalToken)
                .header("X-User-Id", userId.toString())
                .DELETE()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return ResponseEntity.status(resp.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp.body());
    }
}

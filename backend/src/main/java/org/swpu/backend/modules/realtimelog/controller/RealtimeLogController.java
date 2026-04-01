package org.swpu.backend.modules.realtimelog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.swpu.backend.common.security.AuthContextService;
import org.swpu.backend.modules.realtimelog.model.RealtimeLogEntry;
import org.swpu.backend.modules.realtimelog.service.RealtimeLogHub;

@RestController
@RequestMapping("/api/v1/realtime-logs")
public class RealtimeLogController {
    private static final Duration RAG_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RAG_READ_TIMEOUT = Duration.ofMinutes(30);

    private final AuthContextService authContextService;
    private final RealtimeLogHub realtimeLogHub;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String ragLogsUrl;

    public RealtimeLogController(
            AuthContextService authContextService,
            RealtimeLogHub realtimeLogHub,
            ObjectMapper objectMapper,
            @Value("${rag.base-url:http://localhost:8000}") String ragBaseUrl) {
        this.authContextService = authContextService;
        this.realtimeLogHub = realtimeLogHub;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(RAG_CONNECT_TIMEOUT)
                .build();
        this.ragLogsUrl = normalizeBaseUrl(ragBaseUrl) + "/rag/v1/logs/stream";
    }

    @GetMapping(value = "/backend", produces = "application/x-ndjson")
    public StreamingResponseBody backendLogs(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "token", required = false) String token) {
        requireAdmin(authorization, token);
        return outputStream -> streamBackendLogs(outputStream);
    }

    @GetMapping(value = "/rag", produces = "application/x-ndjson")
    public StreamingResponseBody ragLogs(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "token", required = false) String token) {
        requireAdmin(authorization, token);
        return outputStream -> streamRagLogs(outputStream);
    }

    private void streamBackendLogs(OutputStream outputStream) throws IOException {
        LinkedBlockingDeque<RealtimeLogEntry> queue = realtimeLogHub.subscribe();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                RealtimeLogEntry entry = realtimeLogHub.poll(queue, 15, TimeUnit.SECONDS);
                writeEntry(outputStream, entry == null ? realtimeLogHub.heartbeat() : entry);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            realtimeLogHub.unsubscribe(queue);
        }
    }

    private void streamRagLogs(OutputStream outputStream) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ragLogsUrl))
                .GET()
                .header("Accept", "application/x-ndjson")
                .timeout(RAG_READ_TIMEOUT)
                .build();
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("rag log stream request failed with status " + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                    outputStream.write('\n');
                    outputStream.flush();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void requireAdmin(String authorization, String token) {
        AuthContextService.CurrentUser currentUser = authContextService.resolveRequired(resolveBearerToken(authorization, token));
        if (!currentUser.isAdmin()) {
            throw new org.swpu.backend.common.exception.BusinessException(
                    org.swpu.backend.common.api.CommonErrorCode.UNAUTHORIZED,
                    "admin role required"
            );
        }
    }

    private String resolveBearerToken(String authorization, String token) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        if (token == null || token.isBlank()) {
            return null;
        }
        return "Bearer " + token.trim();
    }

    private void writeEntry(OutputStream outputStream, RealtimeLogEntry entry) throws IOException {
        outputStream.write(objectMapper.writeValueAsBytes(entry));
        outputStream.write('\n');
        outputStream.flush();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8000";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}

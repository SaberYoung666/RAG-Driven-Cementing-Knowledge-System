package org.swpu.backend.modules.rag.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.exception.BusinessException;

@Service
public class RagAvailabilityService {
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public RagAvailabilityService(@Value("${rag.base-url:http://localhost:8000}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public RagStatus getStatus() {
        try {
            RagHealthResponse response = webClient.get()
                    .uri("/rag/v1/health")
                    .retrieve()
                    .bodyToMono(RagHealthResponse.class)
                    .block(HEALTH_TIMEOUT);
            if (response == null) {
                return new RagStatus(false, false, false, false, false, false, "RAG 服务未返回健康状态");
            }
            boolean indexReady = Boolean.TRUE.equals(response.indexReady());
            boolean bm25Ready = Boolean.TRUE.equals(response.bm25Ready());
            boolean chatReady = indexReady && bm25Ready;
            boolean serviceAvailable = true;
            String message = chatReady
                    ? "RAG 已就绪"
                    : "RAG 正在准备知识库，请等待索引加载完成";
            return new RagStatus(
                    true,
                    serviceAvailable,
                    chatReady,
                    indexReady,
                    bm25Ready,
                    Boolean.TRUE.equals(response.llmEnabled()),
                    message
            );
        } catch (WebClientRequestException ex) {
            return new RagStatus(false, false, false, false, false, false, "RAG 服务未连接，请等待服务启动完成");
        } catch (Exception ex) {
            return new RagStatus(false, false, false, false, false, false, "RAG 健康检查失败，请稍后重试");
        }
    }

    public void requireChatReady() {
        RagStatus status = getStatus();
        if (!status.connected() || !status.serviceAvailable()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "RAG 服务未连接，请等待服务启动完成");
        }
        if (!status.chatReady()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "RAG 正在准备知识库，请等待索引加载完成后再发送对话或评估");
        }
    }

    public void requireProcessingAvailable() {
        RagStatus status = getStatus();
        if (!status.connected() || !status.serviceAvailable()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "RAG 服务未连接，请等待服务启动完成后再处理文档");
        }
    }

    public record RagStatus(
            boolean connected,
            boolean serviceAvailable,
            boolean chatReady,
            boolean indexReady,
            boolean bm25Ready,
            boolean llmEnabled,
            String message
    ) {
    }

    private record RagHealthResponse(
            String status,
            Boolean indexReady,
            Boolean bm25Ready,
            Boolean rerankEnabled,
            Boolean llmEnabled
    ) {
    }
}

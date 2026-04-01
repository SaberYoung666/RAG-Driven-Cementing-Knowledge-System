package org.swpu.backend.modules.docs.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DocsRagClient {
	private final WebClient webClient;
	private final String processPath;
	private final String statusPathTemplate;

	public DocsRagClient(
			@Value("${rag.base-url:http://localhost:8000}") String baseUrl,
			@Value("${rag.docs-process-path:/rag/v1/docs/process}") String processPath,
			@Value("${rag.ingest-status-path-template:/rag/v1/ingest/{docId}/status}") String statusPathTemplate) {
		this.webClient = WebClient.builder().baseUrl(baseUrl).build();
		this.processPath = processPath;
		this.statusPathTemplate = statusPathTemplate;
	}

	// 向RAG微服务发送异步文档处理请求
	public Mono<RagProcessResponse> processDocs(Long userId, List<RagDocItem> docs) {
		List<String> docIds = docs == null ? List.of() : docs.stream().map(RagDocItem::docId).toList();
		RagProcessRequest request = new RagProcessRequest(userId, docIds, docs == null ? List.of() : docs, "auto", false, true, true);
		return webClient.post()
				.uri(processPath)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(RagProcessResponse.class);
	}

	// 获取文档导入状态
	public Mono<RagIngestStatus> getIngestStatus(String docId) {
		return webClient.get()
				.uri(statusPathTemplate, docId)
				.retrieve()
				.bodyToMono(RagIngestStatus.class);
	}

	public record RagDocItem(String docId, String filePath, String sourceName) {
	}

	private record RagProcessRequest(
			Long userId,
			List<String> docIds,
			List<RagDocItem> docs,
			String enableOcr,
			boolean strictOcr,
			boolean rebuildFaiss,
			boolean rebuildBm25
	) {
	}

	public record RagProcessResponse(
			boolean ok,
			int files,
			int chunks,
			@JsonAlias({"total_chunks"})
			int totalChunks,
			@JsonAlias({"chunks_by_source"})
			Map<String, Integer> chunksBySource,
			@JsonAlias({"processed_doc_ids"})
			List<String> processedDocIds,
			String message
	) {
	}

	public record RagIngestStatus(
			@JsonAlias({"doc_id"})
			String docId,
			String status,
			@JsonAlias({"pages_processed"})
			Integer pagesProcessed,
			@JsonAlias({"ocr_pages"})
			Integer ocrPages,
			String message,
			@JsonAlias({"chunk_count"})
			Integer chunkCount,
			String error,
			@JsonAlias({"failed_pages"})
			List<Integer> failedPages,
			@JsonAlias({"started_at"})
			String startedAt,
			@JsonAlias({"finished_at"})
			String finishedAt,
			@JsonAlias({"updated_at"})
			String updatedAt,
			@JsonAlias({"elapsed_ms"})
			Integer elapsedMs
	) {
	}
}

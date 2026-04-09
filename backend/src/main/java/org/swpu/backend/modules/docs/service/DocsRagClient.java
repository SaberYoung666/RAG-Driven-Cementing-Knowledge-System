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
	private final String buildFaissPath;
	private final String buildBm25Path;

	public DocsRagClient(
			@Value("${rag.base-url:http://localhost:8000}") String baseUrl,
			@Value("${rag.docs-process-path:/rag/v1/docs/process}") String processPath,
			@Value("${rag.ingest-status-path-template:/rag/v1/ingest/{docId}/status}") String statusPathTemplate,
			@Value("${rag.index-build-faiss-path:/rag/v1/index/build/faiss}") String buildFaissPath,
			@Value("${rag.index-build-bm25-path:/rag/v1/index/build/bm25}") String buildBm25Path) {
		this.webClient = WebClient.builder().baseUrl(baseUrl).build();
		this.processPath = processPath;
		this.statusPathTemplate = statusPathTemplate;
		this.buildFaissPath = buildFaissPath;
		this.buildBm25Path = buildBm25Path;
	}

	// 向RAG微服务发送异步文档处理请求
	public Mono<RagProcessResponse> processDocs(Long userId, List<RagDocItem> docs) {
		List<String> docIds = docs == null ? List.of() : docs.stream().map(RagDocItem::docId).toList();
		RagProcessRequest request = new RagProcessRequest(userId, docIds, docs == null ? List.of() : docs, "auto", false, false, false);
		return webClient.post()
				.uri(processPath)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(RagProcessResponse.class);
	}

	public Mono<RagIndexBuildResponse> buildFaiss() {
		return webClient.post()
				.uri(buildFaissPath)
				.bodyValue(new RagIndexBuildRequest(null))
				.retrieve()
				.bodyToMono(RagIndexBuildResponse.class);
	}

	public Mono<RagIndexBuildResponse> buildBm25() {
		return webClient.post()
				.uri(buildBm25Path)
				.bodyValue(new RagIndexBuildRequest(null))
				.retrieve()
				.bodyToMono(RagIndexBuildResponse.class);
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

	private record RagIndexBuildRequest(
			@JsonAlias({"chunks_path"})
			String chunksPath
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

	public record RagIndexBuildResponse(
			boolean ok,
			String message
	) {
	}

	public record RagIngestStatus(
			@JsonAlias({"doc_id"})
			String docId,
			String status,
			Integer progress,
			@JsonAlias({"stage_progress"})
			Integer stageProgress,
			@JsonAlias({"pages_processed"})
			Integer pagesProcessed,
			@JsonAlias({"total_pages"})
			Integer totalPages,
			@JsonAlias({"current_page"})
			Integer currentPage,
			@JsonAlias({"ocr_pages"})
			Integer ocrPages,
			String message,
			@JsonAlias({"chunk_count"})
			Integer chunkCount,
			String error,
			@JsonAlias({"trace_id"})
			String traceId,
			@JsonAlias({"error_type"})
			String errorType,
			@JsonAlias({"failed_stage"})
			String failedStage,
			@JsonAlias({"debug_detail"})
			String debugDetail,
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

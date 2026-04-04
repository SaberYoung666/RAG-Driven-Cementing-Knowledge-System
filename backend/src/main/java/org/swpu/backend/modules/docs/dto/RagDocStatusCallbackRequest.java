package org.swpu.backend.modules.docs.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "RAG 文档状态回调请求")
public record RagDocStatusCallbackRequest(
		@Schema(description = "文档 ID")
		@JsonAlias({"docId", "doc_id"})
		String docId,
		@Schema(description = "状态")
		String status,
		@Schema(description = "进度")
		Integer progress,
		@Schema(description = "阶段进度")
		@JsonAlias({"stageProgress", "stage_progress"})
		Integer stageProgress,
		@Schema(description = "已处理页数")
		@JsonAlias({"pagesProcessed", "pages_processed"})
		Integer pagesProcessed,
		@Schema(description = "总页数")
		@JsonAlias({"totalPages", "total_pages"})
		Integer totalPages,
		@Schema(description = "当前页")
		@JsonAlias({"currentPage", "current_page"})
		Integer currentPage,
		@Schema(description = "OCR 页数")
		@JsonAlias({"ocrPages", "ocr_pages"})
		Integer ocrPages,
		@Schema(description = "分块数")
		@JsonAlias({"chunkCount", "chunk_count"})
		Integer chunkCount,
		@Schema(description = "消息")
		String message,
		@Schema(description = "错误")
		String error,
		@Schema(description = "追踪 ID")
		@JsonAlias({"traceId", "trace_id"})
		String traceId,
		@Schema(description = "异常类型")
		@JsonAlias({"errorType", "error_type"})
		String errorType,
		@Schema(description = "失败阶段")
		@JsonAlias({"failedStage", "failed_stage"})
		String failedStage,
		@Schema(description = "调试详情")
		@JsonAlias({"debugDetail", "debug_detail"})
		String debugDetail,
		@Schema(description = "失败页")
		@JsonAlias({"failedPages", "failed_pages"})
		List<Integer> failedPages,
		@Schema(description = "开始时间")
		@JsonAlias({"startedAt", "started_at"})
		String startedAt,
		@Schema(description = "结束时间")
		@JsonAlias({"finishedAt", "finished_at"})
		String finishedAt,
		@Schema(description = "更新时间")
		@JsonAlias({"updatedAt", "updated_at"})
		String updatedAt,
		@Schema(description = "耗时")
		@JsonAlias({"elapsedMs", "elapsed_ms"})
		Integer elapsedMs
) {
}

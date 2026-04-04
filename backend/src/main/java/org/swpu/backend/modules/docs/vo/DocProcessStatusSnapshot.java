package org.swpu.backend.modules.docs.vo;

import java.util.List;

public record DocProcessStatusSnapshot(
		String docId,
		String status,
		Integer progress,
		Integer stageProgress,
		Integer pagesProcessed,
		Integer totalPages,
		Integer currentPage,
		Integer ocrPages,
		Integer chunkCount,
		String message,
		String error,
		String traceId,
		String errorType,
		String failedStage,
		String debugDetail,
		List<Integer> failedPages,
		String startedAt,
		String finishedAt,
		String updatedAt,
		Integer elapsedMs
) {
}

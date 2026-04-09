package org.swpu.backend.modules.docs.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "重建索引结果")
public record ReindexResult(
		@Schema(description = "是否重建 FAISS")
		boolean rebuildFaiss,
		@Schema(description = "是否重建 BM25")
		boolean rebuildBm25,
		@Schema(description = "FAISS 重建结果消息")
		String faissMessage,
		@Schema(description = "BM25 重建结果消息")
		String bm25Message,
		@Schema(description = "整体结果消息")
		String message
) {
}

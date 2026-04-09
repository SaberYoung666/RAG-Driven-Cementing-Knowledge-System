package org.swpu.backend.modules.docs.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "重建索引请求")
public record ReindexRequest(
		@Schema(description = "是否重建 FAISS 索引", example = "true")
		Boolean rebuildFaiss,
		@Schema(description = "是否重建 BM25 索引", example = "true")
		Boolean rebuildBm25
) {
}

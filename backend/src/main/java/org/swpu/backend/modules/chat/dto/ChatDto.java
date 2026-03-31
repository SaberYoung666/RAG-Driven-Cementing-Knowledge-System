package org.swpu.backend.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;

public class ChatDto {

	// 前端 -> Spring -> Python
	@Schema(description = "RAG 问答请求")
	public record ChatReq(
			@Schema(description = "用户问题", example = "固井顶替效率主要由什么因素决定？")
			@NotBlank String query,
			@Schema(description = "会话 ID（可选）", example = "1")
			String sessionId,
			@Schema(description = "最终返回证据条数", example = "6")
			Integer topK,
			@Schema(description = "最终返回证据条数（兼容字段）", example = "6")
			Integer topR,
			@Schema(description = "最小相关度阈值", example = "0.35")
			Double minScore,
			@Schema(description = "是否启用 LLM", example = "true")
			Boolean useLlm,
			@Schema(description = "问答模式", example = "hybrid")
			String mode,
			@Schema(description = "混合检索权重", example = "0.5")
			Double alpha,
			@Schema(description = "是否启用重排", example = "true")
			Boolean rerankOn,
			@Schema(description = "重排候选数", example = "20")
			Integer candidateK,
			@Schema(description = "是否返回候选 chunk", example = "true")
			Boolean returnChunks
	) {}

	@Schema(description = "发往 Python 微服务的问答请求")
	public record RagChatReq(
			String query,
			String mode,
			Integer topR,
			Integer candidateK,
			Double alpha,
			Boolean rerankOn,
			Double minScore,
			Boolean useLlm,
			Boolean returnChunks
	) {}

	@Schema(description = "引用证据")
	public record Citation(
			@Schema(description = "证据 ID", example = "证据1")
			@JsonAlias({"evidence_id"})
			String evidenceId,
			@Schema(description = "相关度分数", example = "0.93")
			Double score,
			@Schema(description = "文档 ID", example = "demo-scan-001")
			@JsonAlias({"doc_id"})
			String docId,
			@Schema(description = "切片 ID", example = "chunk-cem-001")
			@JsonAlias({"chunk_id"})
			String chunkId,
			@Schema(description = "来源", example = "API-RP-10B-2")
			String source,
			@Schema(description = "页码", example = "124")
			Integer page,
			@Schema(description = "章节", example = "5.3 Centralization Mechanism")
			String section
	) {}

	@Schema(description = "召回文本片段")
	public record Retrieved(
			@Schema(description = "切片 ID", example = "chunk-cem-001")
			@JsonAlias({"chunk_id"})
			String chunkId,
			@Schema(description = "相关度分数", example = "0.93")
			Double score,
			@Schema(description = "文本内容")
			String text,
			@Schema(description = "附加元数据")
			Map<String, Object> metadata
	) {}

	@Schema(description = "调试信息")
	public record Debug(
			@JsonAlias({"retrieval_ms"})
			Double retrievalMs,
			@JsonAlias({"rerank_ms"})
			Double rerankMs,
			@JsonAlias({"gen_ms"})
			Double genMs,
			String mode,
			@JsonAlias({"top1_score"})
			Double top1Score
	) {}

	@Schema(description = "RAG 回答结果")
	public record RagResp(
			@Schema(description = "回答内容")
			String answer,
			@Schema(description = "是否拒答", example = "false")
			Boolean refused,
			@Schema(description = "证据列表")
			List<Citation> citations,
			@Schema(description = "召回片段列表")
			List<Retrieved> retrieved,
			@Schema(description = "调试信息")
			Debug debug,
			@Schema(description = "端到端耗时毫秒", example = "86")
			Integer latencyMs
	) {}
}

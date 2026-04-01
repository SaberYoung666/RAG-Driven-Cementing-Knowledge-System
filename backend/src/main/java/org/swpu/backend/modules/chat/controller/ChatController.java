package org.swpu.backend.modules.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.AuthContextService;
import org.swpu.backend.modules.chat.dto.ChatDto;
import org.swpu.backend.modules.chat.service.CitationDisplayService;
import org.swpu.backend.modules.chat.service.ChatLogService;
import org.swpu.backend.modules.chat.service.RagClient;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.rag.service.RagAvailabilityService;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Chat", description = "RAG 对话接口")
public class ChatController {
	// TODO: 调试固定问答，联调完成后删除。
	private static final String DEBUG_QUERY = "固井顶替效率受哪些因素影响？";
	// TODO: 调试固定问答，联调完成后删除。
	private static final String DEBUG_ANSWER = "固井顶替效率主要受流体流变性与密度差、顶替排量与流动状态、环空几何条件（如套管偏心和井斜角）、套管居中及机械扰动措施、井壁泥饼清洗效果，以及现场施工工艺参数等因素共同影响。其中，套管偏心、钻井液高屈服值和不合理的排量是导致钻井液残留、降低顶替效率的典型原因。";
	private final RagClient ragClient;
	private final CitationDisplayService citationDisplayService;
	private final ChatLogService chatLogService;
	private final SystemLogService systemLogService;
	private final AuthContextService authContextService;
	private final RagAvailabilityService ragAvailabilityService;

	public ChatController(RagClient ragClient, CitationDisplayService citationDisplayService, ChatLogService chatLogService, SystemLogService systemLogService, AuthContextService authContextService, RagAvailabilityService ragAvailabilityService) {
		this.ragClient = ragClient;
		this.citationDisplayService = citationDisplayService;
		this.chatLogService = chatLogService;
		this.systemLogService = systemLogService;
		this.authContextService = authContextService;
		this.ragAvailabilityService = ragAvailabilityService;
	}

	@PostMapping("/chat")
	@Operation(summary = "RAG 问答", description = "输入问题并返回模型回答、引用证据与召回片段")
	@ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数校验失败", content = @Content)})
	public Mono<ApiResponse<ChatDto.RagResp>> chat(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@Valid @RequestBody ChatDto.ChatReq req) {
		AuthContextService.CurrentUser currentUser = authContextService.resolveOptional(authorization);
		ragAvailabilityService.requireChatReady();
		// TODO: 调试固定问答，联调完成后删除。
		if (isDebugQuery(req.query())) {
			ChatDto.RagResp rawResp = new ChatDto.RagResp(DEBUG_ANSWER, false, java.util.List.of(), java.util.List.of(), null, 0);
			chatLogService.recordChat(currentUser == null ? null : currentUser.userId(), req, rawResp);
			ChatDto.RagResp clientResp = citationDisplayService.normalizeRagResp(rawResp);
			recordChatLog(currentUser, req, clientResp, "Served debug answer", true, null);
			return Mono.just(ApiResponse.success(clientResp));
		}

		// 构造发往微服务的请求
		ChatDto.RagChatReq ragReq = toRagReq(req);
		return ragClient.chat(ragReq).map(resp -> {
			ChatDto.RagResp rawResp = normalizeLatency(resp);
			chatLogService.recordChat(currentUser == null ? null : currentUser.userId(), req, rawResp);
			ChatDto.RagResp clientResp = citationDisplayService.normalizeRagResp(rawResp);
			recordChatLog(currentUser, req, clientResp, "Chat completed", true, null);
			return ApiResponse.success(clientResp);
		}).doOnError(ex -> recordChatLog(currentUser, req, null, "Chat failed", false, ex));
	}

	/**
	 * 用于产生发往微服务的请求
	 *
	 * @param req
	 * @return
	 */
	private ChatDto.RagChatReq toRagReq(ChatDto.ChatReq req) {
		// 问答模式
		String mode = (req.mode() == null || req.mode().isBlank()) ? "hybrid" : req.mode();
		// 最终返回证据条数
		int topR = req.topR() != null ? req.topR() : (req.topK() != null ? req.topK() : 6);
		// 重排候选数
		int candidateK = req.candidateK() != null ? req.candidateK() : Math.max(20, topR * 3);
		// 混合检索权重
		double alpha = req.alpha() != null ? req.alpha() : 0.5D;
		// 最小相关度阈值
		double minScore = req.minScore() != null ? req.minScore() : 0.35D;
		// 是否启用重排
		boolean rerankOn = req.rerankOn() == null || req.rerankOn();
		// 是否启用 LLM
		boolean useLlm = req.useLlm() != null && req.useLlm();
		// 是否返回候选 chunk
		boolean returnChunks = req.returnChunks() == null || req.returnChunks();

		return new ChatDto.RagChatReq(req.query(), mode, topR, candidateK, alpha, rerankOn, minScore, useLlm, returnChunks);
	}

	// 标准化延迟
	private ChatDto.RagResp normalizeLatency(ChatDto.RagResp resp) {
		// RAG拒答
		if (resp == null) {
			return new ChatDto.RagResp("", true, java.util.List.of(), java.util.List.of(), null, 0);
		}
		if (resp.latencyMs() != null) {
			return resp;
		}
		// RAG未返回延迟的情况下将三个步骤的延时相加作为延时
		double retrieval = resp.debug() != null && resp.debug().retrievalMs() != null ? resp.debug().retrievalMs() : 0D;
		double rerank = resp.debug() != null && resp.debug().rerankMs() != null ? resp.debug().rerankMs() : 0D;
		double gen = resp.debug() != null && resp.debug().genMs() != null ? resp.debug().genMs() : 0D;
		int latencyMs = (int) Math.round(retrieval + rerank + gen);
		return new ChatDto.RagResp(resp.answer(), resp.refused(), resp.citations(), resp.retrieved(), resp.debug(), latencyMs);
	}

	private boolean isDebugQuery(String query) {
		return DEBUG_QUERY.equals(query == null ? null : query.trim());
	}

	private void recordChatLog(AuthContextService.CurrentUser currentUser, ChatDto.ChatReq req, ChatDto.RagResp resp, String message, boolean success, Throwable ex) {
		int effectiveTopK = req.topR() != null ? req.topR() : (req.topK() != null ? req.topK() : 6);
		String effectiveMode = (req.mode() == null || req.mode().isBlank()) ? "hybrid" : req.mode();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("sessionId", req.sessionId());
		details.put("topK", effectiveTopK);
		details.put("topR", req.topR());
		details.put("mode", effectiveMode);
		details.put("minScore", req.minScore());
		details.put("alpha", req.alpha());
		details.put("rerankOn", req.rerankOn());
		details.put("candidateK", req.candidateK());
		details.put("useLlm", req.useLlm());
		details.put("returnChunks", req.returnChunks());
		if (resp != null) {
			details.put("refused", resp.refused());
			details.put("latencyMs", resp.latencyMs());
			details.put("citationCount", resp.citations() == null ? 0 : resp.citations().size());
			details.put("avgCitationScore", averageCitationScore(resp.citations()));
			details.put("retrievalMs", resp.debug() == null ? null : resp.debug().retrievalMs());
			details.put("rerankMs", resp.debug() == null ? null : resp.debug().rerankMs());
			details.put("genMs", resp.debug() == null ? null : resp.debug().genMs());
			details.put("top1Score", resp.debug() == null ? null : resp.debug().top1Score());
			details.put("docs", toDocHits(resp.citations()));
		}
		systemLogService.record(new SystemLogCommand()
				.setTraceId(TraceContext.getTraceId())
				.setModule("CHAT")
				.setSource(LogConstants.SOURCE_BUSINESS)
				.setAction("CHAT")
				.setLevel(success ? LogConstants.LEVEL_INFO : LogConstants.LEVEL_ERROR)
				.setSuccess(success)
				.setMessage(message)
				.setUserId(currentUser == null ? null : currentUser.userId())
				.setUsername(currentUser == null ? null : currentUser.username())
				.setUserRole(currentUser == null ? null : currentUser.role())
				.setVisibilityScope(currentUser == null ? LogConstants.SCOPE_SYSTEM : LogConstants.SCOPE_PRIVATE)
				.setResourceType("CHAT_SESSION")
				.setResourceId(req.sessionId())
				.setExceptionClass(ex == null ? null : ex.getClass().getName())
				.setDetails(details));
	}

	private Double averageCitationScore(List<ChatDto.Citation> citations) {
		if (citations == null || citations.isEmpty()) {
			return null;
		}
		double sum = 0D;
		int count = 0;
		for (ChatDto.Citation citation : citations) {
			if (citation != null && citation.score() != null) {
				sum += citation.score();
				count++;
			}
		}
		return count == 0 ? null : sum / count;
	}

	private List<Map<String, Object>> toDocHits(List<ChatDto.Citation> citations) {
		if (citations == null || citations.isEmpty()) {
			return List.of();
		}
		List<Map<String, Object>> docs = new ArrayList<>();
		for (ChatDto.Citation citation : citations) {
			if (citation == null) {
				continue;
			}
			Map<String, Object> doc = new LinkedHashMap<>();
			if (citation.docId() != null) {
				doc.put("docId", citation.docId());
			}
			if (citation.source() != null) {
				doc.put("docName", citation.source());
			}
			if (citation.score() != null) {
				doc.put("score", citation.score());
			}
			if (!doc.isEmpty()) {
				docs.add(doc);
			}
		}
		return docs;
	}
}

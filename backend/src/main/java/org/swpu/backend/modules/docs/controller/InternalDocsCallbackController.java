package org.swpu.backend.modules.docs.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.exception.BusinessException;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.modules.docs.dto.RagDocStatusCallbackRequest;
import org.swpu.backend.modules.docs.service.DocsService;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/rag/docs")
public class InternalDocsCallbackController {
	private static final String CALLBACK_SECRET_HEADER = "X-Rag-Callback-Secret";

	private final DocsService docsService;
	private final SystemLogService systemLogService;
	private final String callbackSecret;

	public InternalDocsCallbackController(
			DocsService docsService,
			SystemLogService systemLogService,
			@Value("${rag.status-callback-secret:change-me-rag-callback}") String callbackSecret) {
		this.docsService = docsService;
		this.systemLogService = systemLogService;
		this.callbackSecret = callbackSecret;
	}

	@PostMapping("/status")
	public ApiResponse<Map<String, Object>> acceptStatus(
			@RequestHeader(name = CALLBACK_SECRET_HEADER, required = false) String callbackSecretHeader,
			@Valid @RequestBody RagDocStatusCallbackRequest request) {
		if (!StringUtils.hasText(callbackSecretHeader) || !callbackSecretHeader.equals(callbackSecret)) {
			recordFailedCallback("invalid callback secret", request);
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "invalid rag callback secret");
		}
		docsService.acceptRagStatusCallback(request);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("ok", true);
		data.put("docId", request.docId());
		data.put("status", request.status());
		return ApiResponse.success(data);
	}

	private void recordFailedCallback(String message, RagDocStatusCallbackRequest request) {
		systemLogService.record(new SystemLogCommand()
				.setTraceId(firstNonBlank(request == null ? null : request.traceId(), TraceContext.getTraceId()))
				.setModule("DOCS")
				.setSource(LogConstants.SOURCE_REQUEST)
				.setAction("RAG_STATUS_CALLBACK_FAILED")
				.setLevel(LogConstants.LEVEL_ERROR)
				.setSuccess(false)
				.setMessage(message)
				.setVisibilityScope(LogConstants.SCOPE_SYSTEM)
				.setResourceType("DOC")
				.setResourceId(request == null ? null : request.docId())
				.setDetails(mapOf(
						"docId", request == null ? null : request.docId(),
						"status", request == null ? null : request.status(),
						"traceId", request == null ? null : request.traceId()
				)));
	}

	private Map<String, Object> mapOf(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (pairs[i] != null && pairs[i + 1] != null) {
				map.put(String.valueOf(pairs[i]), pairs[i + 1]);
			}
		}
		return map;
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value.trim();
			}
		}
		return null;
	}
}

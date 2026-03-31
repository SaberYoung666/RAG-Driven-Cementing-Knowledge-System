package org.swpu.backend.modules.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.chat.service.ChatLogService;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Logs", description = "问答日志与反馈")
public class LogsController {
	private final ChatLogService chatLogService;

	public LogsController(ChatLogService chatLogService) {
		this.chatLogService = chatLogService;
	}

	@GetMapping("/logs")
	@Operation(summary = "分页查询问答日志")
	public ApiResponse<PageResult<ChatLogService.LogEntry>> listLogs(
			@RequestParam(name = "page", defaultValue = "1") Integer page,
			@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
			@RequestParam(name = "sessionId", required = false) String sessionId,
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "refused", required = false) Boolean refused) {
		return ApiResponse.success(chatLogService.listLogs(page, pageSize, sessionId, keyword, refused));
	}

	@PostMapping("/feedback")
	@Operation(summary = "提交日志反馈")
	public ApiResponse<ChatLogService.FeedbackResult> feedback(@Valid @RequestBody ChatLogService.FeedbackRequest request) {
		return ApiResponse.success(chatLogService.saveFeedback(request));
	}
}

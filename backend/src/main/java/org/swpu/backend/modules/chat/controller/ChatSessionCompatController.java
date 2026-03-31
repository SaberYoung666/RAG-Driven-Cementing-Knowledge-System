package org.swpu.backend.modules.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.chat.service.ChatLogService;
import org.swpu.backend.modules.session.dto.CreateSessionRequest;
import org.swpu.backend.modules.session.dto.SessionQuery;
import org.swpu.backend.modules.session.dto.UpdateSessionTitleRequest;
import org.swpu.backend.modules.session.service.SessionService;
import org.swpu.backend.modules.session.vo.ChatSessionVo;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "ChatSessionCompat", description = "兼容前端会话 API")
public class ChatSessionCompatController {
	private final SessionService sessionService;
	private final ChatLogService chatLogService;

	public ChatSessionCompatController(SessionService sessionService, ChatLogService chatLogService) {
		this.sessionService = sessionService;
		this.chatLogService = chatLogService;
	}

	@PostMapping("/sessions")
	@Operation(summary = "创建会话（兼容路径）")
	public ApiResponse<SessionItem> createSession(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestBody(required = false) CreateCompatSessionRequest request) {
		CreateSessionRequest req = new CreateSessionRequest();
		req.setTitle(request == null ? null : request.title());
		return ApiResponse.success(toSessionItem(sessionService.createSession(authorization, req), null));
	}

	@GetMapping("/sessions")
	@Operation(summary = "分页查询会话（兼容路径）")
	public ApiResponse<PageResult<SessionItem>> listSessions(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestParam(name = "page", defaultValue = "1") Integer page,
			@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
			@RequestParam(name = "keyword", required = false) String keyword) {
		SessionQuery query = new SessionQuery();
		query.setPage(page);
		query.setPageSize(pageSize);
		query.setKeyword(keyword);

		PageResult<ChatSessionVo> raw = sessionService.listSessions(authorization, query);
		List<SessionItem> mapped = raw.getItems().stream().map(x -> toSessionItem(x, x.updatedAt())).toList();
		return ApiResponse.success(PageResult.of(mapped, raw.getTotal(), raw.getPage(), raw.getSize()));
	}

	@GetMapping("/sessions/{sessionId}")
	@Operation(summary = "会话详情（兼容路径）")
	public ApiResponse<SessionDetail> getSession(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable Long sessionId) {
		ChatSessionVo session = sessionService.getSession(authorization, sessionId);
		List<ChatLogService.SessionMessage> messages = chatLogService.getSessionMessages(sessionId);
		return ApiResponse.success(
				new SessionDetail(
						String.valueOf(session.id()),
						session.title(),
						session.createdAt(),
						session.updatedAt(),
						messages
				)
		);
	}

	@PatchMapping("/sessions/{sessionId}")
	@Operation(summary = "更新会话标题（兼容路径）")
	public ApiResponse<UpdateResult> updateSessionTitle(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable Long sessionId,
			@Valid @RequestBody UpdateCompatSessionTitleRequest request) {
		UpdateSessionTitleRequest req = new UpdateSessionTitleRequest();
		req.setTitle(request.title());
		sessionService.updateSessionTitle(authorization, sessionId, req);
		return ApiResponse.success(new UpdateResult(true));
	}

	@DeleteMapping("/sessions/{sessionId}")
	@Operation(summary = "删除会话（兼容路径）")
	public ApiResponse<DeleteResult> deleteSession(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable Long sessionId) {
		return ApiResponse.success(new DeleteResult(sessionService.deleteSession(authorization, sessionId)));
	}

	private SessionItem toSessionItem(ChatSessionVo raw, String lastMessageAt) {
		return new SessionItem(
				String.valueOf(raw.id()),
				raw.title(),
				raw.createdAt(),
				raw.updatedAt(),
				lastMessageAt
		);
	}

	public record CreateCompatSessionRequest(String title) {
	}

	public record UpdateCompatSessionTitleRequest(@NotBlank String title) {
	}

	public record SessionItem(
			String sessionId,
			String title,
			String createdAt,
			String updatedAt,
			String lastMessageAt
	) {
	}

	public record SessionDetail(
			String sessionId,
			String title,
			String createdAt,
			String updatedAt,
			List<ChatLogService.SessionMessage> messages
	) {
	}

	public record UpdateResult(boolean updated) {
	}

	public record DeleteResult(boolean deleted) {
	}
}

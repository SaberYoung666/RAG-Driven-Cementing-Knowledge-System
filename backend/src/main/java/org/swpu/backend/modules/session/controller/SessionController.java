package org.swpu.backend.modules.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.session.dto.CreateSessionRequest;
import org.swpu.backend.modules.session.dto.SessionQuery;
import org.swpu.backend.modules.session.dto.UpdateSessionTitleRequest;
import org.swpu.backend.modules.session.vo.SessionDeleteResult;
import org.swpu.backend.modules.session.service.SessionService;
import org.swpu.backend.modules.session.vo.ChatSessionVo;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Session", description = "会话管理接口")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    @Operation(summary = "创建会话", description = "创建当前登录用户的新会话", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权", content = @Content)
    })
    public ApiResponse<ChatSessionVo> createSession(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody(required = false) CreateSessionRequest request) {
        return ApiResponse.success(sessionService.createSession(authorization, request));
    }

    @GetMapping("/sessions")
    @Operation(summary = "分页查询会话", description = "分页查询当前登录用户的会话列表", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权", content = @Content)
    })
    public ApiResponse<PageResult<ChatSessionVo>> listSessions(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @ParameterObject @ModelAttribute SessionQuery query) {
        return ApiResponse.success(sessionService.listSessions(authorization, query));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "查询会话详情", description = "查询当前登录用户指定会话详情", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "会话不存在", content = @Content)
    })
    public ApiResponse<ChatSessionVo> getSession(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long sessionId) {
        return ApiResponse.success(sessionService.getSession(authorization, sessionId));
    }

    @PatchMapping("/sessions/{sessionId}/title")
    @Operation(summary = "更新会话标题", description = "更新当前登录用户指定会话标题", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "会话不存在", content = @Content)
    })
    public ApiResponse<ChatSessionVo> updateSessionTitle(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionTitleRequest request) {
        return ApiResponse.success(sessionService.updateSessionTitle(authorization, sessionId, request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除会话", description = "删除当前登录用户指定会话", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除结果",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权", content = @Content)
    })
    public ApiResponse<SessionDeleteResult> deleteSession(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long sessionId) {
        return ApiResponse.success(new SessionDeleteResult(sessionService.deleteSession(authorization, sessionId)));
    }
}

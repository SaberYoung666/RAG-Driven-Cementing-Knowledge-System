package org.swpu.backend.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.modules.auth.dto.LoginRequest;
import org.swpu.backend.modules.auth.dto.RegisterRequest;
import org.swpu.backend.modules.auth.service.AuthService;
import org.swpu.backend.modules.auth.vo.AuthTokenVo;
import org.swpu.backend.modules.auth.vo.UsernameCheckVo;
import org.swpu.backend.modules.auth.vo.UserProfileVo;

// 认证接口
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "用户认证接口")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册成功后直接返回访问令牌")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "注册成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数校验失败", content = @Content)
    })
    public ApiResponse<AuthTokenVo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @GetMapping("/check-username")
    @Operation(summary = "检查用户名是否可用", description = "注册前检查用户名是否已被占用")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "检查成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "用户名为空", content = @Content)
    })
    public ApiResponse<UsernameCheckVo> checkUsernameAvailability(
            @Parameter(description = "待检查用户名", required = true, example = "new_user")
            @RequestParam("username") String username) {
        return ApiResponse.success(authService.checkUsernameAvailability(username));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回访问令牌")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误", content = @Content)
    })
    public ApiResponse<AuthTokenVo> login(@Valid @RequestBody LoginRequest request) {
        log.info("用户{}正在登录", request.getUsername());
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息",
            description = "从 Authorization 头解析 token 并返回用户信息",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或 token 无效", content = @Content)
    })
    public ApiResponse<UserProfileVo> me(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.me(authorization));
    }
}

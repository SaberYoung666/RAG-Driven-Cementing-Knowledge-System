package org.swpu.backend.modules.logging.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.logging.dto.SystemLogOverviewQuery;
import org.swpu.backend.modules.logging.dto.SystemLogQuery;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.logging.vo.SystemLogOverviewVo;
import org.swpu.backend.modules.logging.vo.SystemLogVo;

@RestController
@RequestMapping("/api/v1/system-logs")
@Tag(name = "SystemLog", description = "系统日志查询接口")
public class SystemLogController {
    private final SystemLogService systemLogService;

    public SystemLogController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @GetMapping
    @Operation(summary = "分页查询系统日志", security = {@SecurityRequirement(name = "bearerAuth")})
    public ApiResponse<PageResult<SystemLogVo>> listLogs(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @ParameterObject @ModelAttribute SystemLogQuery query) {
        return ApiResponse.success(systemLogService.listLogs(authorization, query));
    }

    @GetMapping("/overview")
    @Operation(summary = "日志总览分析", security = {@SecurityRequirement(name = "bearerAuth")})
    public ApiResponse<SystemLogOverviewVo> overview(
            @Parameter(description = "Bearer Token，格式：Bearer {token}")
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @ParameterObject @ModelAttribute SystemLogOverviewQuery query) {
        return ApiResponse.success(systemLogService.overview(authorization, query));
    }
}

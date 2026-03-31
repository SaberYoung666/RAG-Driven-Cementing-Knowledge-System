package org.swpu.backend.modules.logging.vo;

public record SystemLogVo(
        Long id,
        String traceId,
        Long userId,
        String username,
        String userRole,
        String visibilityScope,
        String module,
        String source,
        String action,
        String level,
        Boolean success,
        String message,
        String detailsJson,
        String resourceType,
        String resourceId,
        String httpMethod,
        String requestPath,
        Integer statusCode,
        String clientIp,
        Long durationMs,
        String exceptionClass,
        String createdAt,
        String startedAt,
        String finishedAt
) {
}

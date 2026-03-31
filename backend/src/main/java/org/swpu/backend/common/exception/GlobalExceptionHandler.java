package org.swpu.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.AuthContextService;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.logging.support.RequestLogInterceptor;

// 全局异常处理：统一包装为 ApiResponse
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final SystemLogService systemLogService;
    private final AuthContextService authContextService;

    public GlobalExceptionHandler(SystemLogService systemLogService, AuthContextService authContextService) {
        this.systemLogService = systemLogService;
        this.authContextService = authContextService;
    }

    // 处理业务异常：按业务码返回
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: code={}, message={}", ex.getCode(), ex.getMessage());
        markRequest(request, ex.getCode(), false, ex.getMessage());
        recordException("WARN", ex, request, ex.getCode(), ex.getMessage());
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    // 处理参数校验异常：返回统一 bad request
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ApiResponse<Void> handleValidationException(Exception ex, HttpServletRequest request) {
        log.warn("Validation exception: {}", ex.getMessage());
        markRequest(request, CommonErrorCode.BAD_REQUEST.getCode(), false, CommonErrorCode.BAD_REQUEST.getMessage());
        recordException("WARN", ex, request, CommonErrorCode.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST.getMessage());
        return ApiResponse.error(CommonErrorCode.BAD_REQUEST, CommonErrorCode.BAD_REQUEST.getMessage());
    }

    // 处理未捕获异常：返回系统内部错误
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        markRequest(request, CommonErrorCode.INTERNAL_ERROR.getCode(), false, CommonErrorCode.INTERNAL_ERROR.getMessage());
        recordException("ERROR", ex, request, CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage());
        return ApiResponse.error(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.getMessage());
    }

    private void markRequest(HttpServletRequest request, int code, boolean success, String message) {
        if (request == null) {
            return;
        }
        request.setAttribute(RequestLogInterceptor.ATTR_LOGICAL_CODE, code);
        request.setAttribute(RequestLogInterceptor.ATTR_LOGICAL_SUCCESS, success);
        request.setAttribute(RequestLogInterceptor.ATTR_LOGICAL_MESSAGE, message);
    }

    private void recordException(String level, Exception ex, HttpServletRequest request, int code, String message) {
        AuthContextService.CurrentUser currentUser = authContextService.currentFromRequest();
        SystemLogCommand command = new SystemLogCommand()
                .setTraceId(TraceContext.getTraceId())
                .setModule(resolveModule(request))
                .setSource(LogConstants.SOURCE_EXCEPTION)
                .setAction(request == null ? "UNHANDLED_EXCEPTION" : request.getMethod() + " " + request.getRequestURI())
                .setLevel(level)
                .setSuccess(false)
                .setMessage(message)
                .setStatusCode(code)
                .setExceptionClass(ex.getClass().getName())
                .setHttpMethod(request == null ? null : request.getMethod())
                .setRequestPath(request == null ? null : request.getRequestURI())
                .setClientIp(request == null ? null : request.getRemoteAddr())
                .setDetails(buildExceptionDetails(ex));
        if (currentUser != null) {
            command.setUserId(currentUser.userId())
                    .setUsername(currentUser.username())
                    .setUserRole(currentUser.role())
                    .setVisibilityScope(LogConstants.SCOPE_PRIVATE);
        } else {
            command.setVisibilityScope(LogConstants.SCOPE_SYSTEM);
        }
        systemLogService.record(command);
    }

    private Map<String, Object> buildExceptionDetails(Exception ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex != null && ex.getMessage() != null) {
            details.put("exceptionMessage", ex.getMessage());
        }
        return details;
    }

    private String resolveModule(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return "SYSTEM";
        }
        String path = request.getRequestURI();
        if (path.contains("/auth")) {
            return "AUTH";
        }
        if (path.contains("/docs") || path.contains("/ingest")) {
            return "DOCS";
        }
        if (path.contains("/chat")) {
            return "CHAT";
        }
        if (path.contains("/eval")) {
            return "EVAL";
        }
        if (path.contains("/sessions")) {
            return "SESSION";
        }
        if (path.contains("/system-logs")) {
            return "LOGGING";
        }
        return "SYSTEM";
    }
}

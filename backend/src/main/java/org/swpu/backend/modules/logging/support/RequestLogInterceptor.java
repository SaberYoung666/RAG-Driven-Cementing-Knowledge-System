package org.swpu.backend.modules.logging.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.AuthContextService;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;

@Component
public class RequestLogInterceptor implements HandlerInterceptor {
    public static final String ATTR_TRACE_ID = "request.log.traceId";
    public static final String ATTR_START_TIME = "request.log.startTime";
    public static final String ATTR_USER = "request.log.user";
    public static final String ATTR_LOGICAL_CODE = "request.log.logicalCode";
    public static final String ATTR_LOGICAL_SUCCESS = "request.log.logicalSuccess";
    public static final String ATTR_LOGICAL_MESSAGE = "request.log.logicalMessage";

    private final SystemLogService systemLogService;
    private final AuthContextService authContextService;

    public RequestLogInterceptor(SystemLogService systemLogService, AuthContextService authContextService) {
        this.systemLogService = systemLogService;
        this.authContextService = authContextService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = UUID.randomUUID().toString();
        TraceContext.setTraceId(traceId);
        request.setAttribute(ATTR_TRACE_ID, traceId);
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());

        AuthContextService.CurrentUser currentUser = authContextService.resolveOptional(request.getHeader("Authorization"));
        if (currentUser != null) {
            request.setAttribute(ATTR_USER, currentUser);
            request.setAttribute(AuthContextService.CurrentUser.class.getName(), currentUser);
        }

        systemLogService.record(baseCommand(request, currentUser)
                .setTraceId(traceId)
                .setModule(resolveModule(request.getRequestURI()))
                .setSource(LogConstants.SOURCE_REQUEST)
                .setAction(request.getMethod() + " " + request.getRequestURI())
                .setLevel(LogConstants.LEVEL_INFO)
                .setSuccess(true)
                .setMessage("Request started")
                .setStatusCode(0)
                .setStartedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .setDetails(singleDetail("queryString", safe(request.getQueryString()))));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String traceId = (String) request.getAttribute(ATTR_TRACE_ID);
        Long start = (Long) request.getAttribute(ATTR_START_TIME);
        long durationMs = start == null ? 0L : Math.max(0L, System.currentTimeMillis() - start);
        AuthContextService.CurrentUser currentUser = (AuthContextService.CurrentUser) request.getAttribute(ATTR_USER);

        Integer logicalCode = request.getAttribute(ATTR_LOGICAL_CODE) instanceof Integer code ? code : response.getStatus();
        boolean logicalSuccess = !(request.getAttribute(ATTR_LOGICAL_SUCCESS) instanceof Boolean success) || success;
        String logicalMessage = request.getAttribute(ATTR_LOGICAL_MESSAGE) instanceof String message ? message : (ex == null ? "Request completed" : ex.getMessage());
        String level = ex != null || !logicalSuccess ? LogConstants.LEVEL_ERROR : LogConstants.LEVEL_INFO;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("handler", handler == null ? null : handler.getClass().getSimpleName());
        details.put("httpStatus", response.getStatus());
        details.put("logicalCode", logicalCode);

        systemLogService.record(baseCommand(request, currentUser)
                .setTraceId(traceId)
                .setModule(resolveModule(request.getRequestURI()))
                .setSource(LogConstants.SOURCE_REQUEST)
                .setAction(request.getMethod() + " " + request.getRequestURI())
                .setLevel(level)
                .setSuccess(logicalSuccess && ex == null)
                .setMessage(safe(logicalMessage))
                .setStatusCode(logicalCode)
                .setDurationMs(durationMs)
                .setExceptionClass(ex == null ? null : ex.getClass().getName())
                .setStartedAt(start == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneOffset.UTC))
                .setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .setDetails(details));

        TraceContext.clear();
    }

    private SystemLogCommand baseCommand(HttpServletRequest request, AuthContextService.CurrentUser currentUser) {
        SystemLogCommand command = new SystemLogCommand()
                .setHttpMethod(request.getMethod())
                .setRequestPath(request.getRequestURI())
                .setClientIp(resolveClientIp(request));
        if (currentUser != null) {
            command.setUserId(currentUser.userId())
                    .setUsername(currentUser.username())
                    .setUserRole(currentUser.role())
                    .setVisibilityScope(LogConstants.SCOPE_PRIVATE);
        } else {
            command.setVisibilityScope(LogConstants.SCOPE_SYSTEM);
        }
        return command;
    }

    private String resolveModule(String path) {
        if (path == null || path.isBlank()) {
            return "SYSTEM";
        }
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

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private String safe(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private Map<String, Object> singleDetail(String key, Object value) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (key != null && value != null) {
            details.put(key, value);
        }
        return details;
    }
}

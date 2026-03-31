package org.swpu.backend.modules.logging.model;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SystemLogCommand {
    private String traceId;
    private Long userId;
    private String username;
    private String userRole;
    private String visibilityScope;
    private String module;
    private String source;
    private String action;
    private String level;
    private Boolean success;
    private String message;
    private Map<String, Object> details;
    private String resourceType;
    private String resourceId;
    private String httpMethod;
    private String requestPath;
    private Integer statusCode;
    private String clientIp;
    private Long durationMs;
    private String exceptionClass;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}

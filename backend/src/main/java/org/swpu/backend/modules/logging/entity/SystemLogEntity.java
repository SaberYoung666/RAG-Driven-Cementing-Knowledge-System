package org.swpu.backend.modules.logging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("system_log")
public class SystemLogEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trace_id")
    private String traceId;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("user_role")
    private String userRole;

    @TableField("visibility_scope")
    private String visibilityScope;

    @TableField("module")
    private String module;

    @TableField("source")
    private String source;

    @TableField("action")
    private String action;

    @TableField("level")
    private String level;

    @TableField("success")
    private Boolean success;

    @TableField("message")
    private String message;

    @TableField("details_json")
    private String detailsJson;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private String resourceId;

    @TableField("http_method")
    private String httpMethod;

    @TableField("request_path")
    private String requestPath;

    @TableField("status_code")
    private Integer statusCode;

    @TableField("client_ip")
    private String clientIp;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("exception_class")
    private String exceptionClass;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    @TableField("finished_at")
    private OffsetDateTime finishedAt;
}

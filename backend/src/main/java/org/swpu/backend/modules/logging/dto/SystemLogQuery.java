package org.swpu.backend.modules.logging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "系统日志查询参数")
public class SystemLogQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String module;
    private String level;
    private String source;
    private String traceId;
    private String action;
    private String keyword;
    private String scope;
    private String from;
    private String to;
    private Long userId;
}

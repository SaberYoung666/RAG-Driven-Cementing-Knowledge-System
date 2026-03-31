package org.swpu.backend.modules.logging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "系统日志总览查询参数")
public class SystemLogOverviewQuery {
    private String from;
    private String to;
    private String granularity = "day";
    private String module;
    private Long userId;
    private String scope = "all";
    private Integer topN = 10;
}

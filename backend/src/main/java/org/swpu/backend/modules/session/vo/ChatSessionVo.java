package org.swpu.backend.modules.session.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话信息")
public record ChatSessionVo(
        @Schema(description = "会话 ID", example = "1")
        Long id,
        @Schema(description = "会话标题", example = "固井替浆参数复盘")
        String title,
        @Schema(description = "创建时间(ISO-8601)", example = "2026-03-03T10:00:00Z")
        String createdAt,
        @Schema(description = "更新时间(ISO-8601)", example = "2026-03-03T10:05:00Z")
        String updatedAt
) {
}

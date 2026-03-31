package org.swpu.backend.modules.session.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话删除结果")
public record SessionDeleteResult(
        @Schema(description = "是否删除成功", example = "true")
        boolean deleted
) {
}

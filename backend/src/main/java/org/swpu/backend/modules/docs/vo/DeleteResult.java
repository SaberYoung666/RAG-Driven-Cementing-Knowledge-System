package org.swpu.backend.modules.docs.vo;

import io.swagger.v3.oas.annotations.media.Schema;

// 删除结果返回对象
@Schema(description = "删除结果")
public record DeleteResult(
        @Schema(description = "是否删除成功", example = "true")
        boolean deleted) {
}

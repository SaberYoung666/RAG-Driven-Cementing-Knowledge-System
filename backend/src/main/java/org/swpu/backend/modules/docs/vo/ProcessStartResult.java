package org.swpu.backend.modules.docs.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "文档处理触发结果")
public record ProcessStartResult(@Schema(description = "本次请求 ID") String requestId,
								 @Schema(description = "已受理文档 ID 列表") List<String> acceptedDocIds,
								 @Schema(description = "受理数量", example = "2") int acceptedCount) {
}

package org.swpu.backend.modules.docs.vo;

import io.swagger.v3.oas.annotations.media.Schema;

// 上传入库响应
@Schema(description = "文档入库任务响应")
public record IngestResult(
        @Schema(description = "入库任务 ID", example = "job-20260303-001")
        String jobId,
        @Schema(description = "是否已受理", example = "true")
        boolean accepted,
        @Schema(description = "任务消息", example = "accepted")
        String message) {
}

package org.swpu.backend.modules.docs.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文档处理进度信息")
public record DocProcessInfo(
		@Schema(description = "文档 ID")
		String docId,
		@Schema(description = "处理状态")
		String status,
		@Schema(description = "进度百分比")
		Integer progress,
		@Schema(description = "处理阶段")
		String stage,
		@Schema(description = "说明")
		String message,
		@Schema(description = "分块数量")
		Integer chunkCount,
		@Schema(description = "更新时间")
		String updatedAt,
		@Schema(description = "详情")
		String detail,
		@Schema(description = "追踪 ID")
		String traceId,
		@Schema(description = "异常类型")
		String errorType,
		@Schema(description = "失败阶段")
		String failedStage,
		@Schema(description = "调试详情")
		String debugDetail
) {
}

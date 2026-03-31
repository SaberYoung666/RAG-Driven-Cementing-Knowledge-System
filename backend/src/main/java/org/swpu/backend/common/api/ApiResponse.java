package org.swpu.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;

// 统一返回体：code/message/data/traceId/ts
@Schema(description = "统一响应体")
public final class ApiResponse<T> {
    @Schema(description = "业务状态码", example = "0")
    private final int code;
    @Schema(description = "响应消息", example = "OK")
    private final String message;
    @Schema(description = "响应数据")
    private final T data;
    @Schema(description = "链路追踪 ID")
    private final String traceId;
    @Schema(description = "响应时间戳(ISO-8601)")
    private final String ts;

    private ApiResponse(int code, String message, T data, String traceId, String ts) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.ts = ts;
    }

    // 成功返回（无数据）
    public static <T> ApiResponse<T> success() {
        return success(CommonErrorCode.SUCCESS.getMessage(), null);
    }

    // 成功返回（仅数据）
    public static <T> ApiResponse<T> success(T data) {
        return success(CommonErrorCode.SUCCESS.getMessage(), data);
    }

    // 成功返回（自定义提示 + 数据）
    public static <T> ApiResponse<T> success(String message, T data) {
        return of(CommonErrorCode.SUCCESS.getCode(), message, data, null);
    }

    // 错误返回（使用错误码默认提示）
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    // 错误返回（使用错误码自定义提示）
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return of(errorCode.getCode(), message, null, null);
    }

    // 错误返回（自定义错误码与提示）
    public static <T> ApiResponse<T> error(int code, String message) {
        return of(code, message, null, null);
    }

    // 通用构造入口，自动补齐 traceId 和时间戳
    public static <T> ApiResponse<T> of(int code, String message, T data, String traceId) {
        String resolvedTraceId = resolveTraceId(traceId);
        String timestamp = Instant.now().toString();
        return new ApiResponse<>(code, message, data, resolvedTraceId, timestamp);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTs() {
        return ts;
    }

    // 优先使用传入的 traceId，其次从 MDC 读取，最后生成 UUID
    private static String resolveTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String mdcTraceId = MDC.get("traceId");
        if (mdcTraceId == null || mdcTraceId.isBlank()) {
            mdcTraceId = MDC.get("trace_id");
        }
        if (mdcTraceId == null || mdcTraceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return mdcTraceId;
    }
}

package org.swpu.backend.common.api;

// 系统通用错误码
public enum CommonErrorCode implements ErrorCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    PAYLOAD_TOO_LARGE(413, "payload too large"),
    SERVICE_UNAVAILABLE(503, "service unavailable"),
    INTERNAL_ERROR(500, "internal error");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

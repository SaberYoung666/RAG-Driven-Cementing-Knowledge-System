package org.swpu.backend.common.exception;

import org.swpu.backend.common.api.ErrorCode;

// 业务异常：用于可预期的业务错误
public class BusinessException extends RuntimeException {
    private final int code;

    // 通过错误码构造
    public BusinessException(ErrorCode errorCode) {
        super(errorCode == null ? null : errorCode.getMessage());
        this.code = errorCode == null ? -1 : errorCode.getCode();
    }

    // 通过错误码和自定义提示构造
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode == null ? -1 : errorCode.getCode();
    }

    // 通过自定义错误码和提示构造
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    // 获取业务错误码
    public int getCode() {
        return code;
    }
}

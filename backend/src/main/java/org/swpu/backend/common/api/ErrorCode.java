package org.swpu.backend.common.api;

// 统一错误码接口
public interface ErrorCode {
    // 错误码（0 表示成功，非 0 表示错误）
    int getCode();

    // 错误提示信息
    String getMessage();
}

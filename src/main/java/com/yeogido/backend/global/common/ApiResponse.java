package com.yeogido.backend.global.common;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Builder;

@Builder
public record ApiResponse<T>(
        boolean isSuccess,
        String code,
        String message,
        T result
) {

    public static <T> ApiResponse<T> onSuccess(BaseResponse successCode, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .code(successCode.getCode())
                .message(successCode.getMessage())
                .result(result)
                .build();
    }

    public static ApiResponse<Void> onSuccess(BaseResponse successCode) {
        return ApiResponse.<Void>builder()
                .isSuccess(true)
                .code(successCode.getCode())
                .message(successCode.getMessage())
                .result(null)
                .build();
    }

    public static ApiResponse<Void> onFailure(ErrorCode errorCode) {
        return ApiResponse.<Void>builder()
                .isSuccess(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .result(null)
                .build();
    }
}
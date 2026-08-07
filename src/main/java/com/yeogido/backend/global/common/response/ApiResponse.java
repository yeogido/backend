package com.yeogido.backend.global.common.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder
public record ApiResponse<T>(
        boolean isSuccess,
        String code,
        String message,
        T result,
        @JsonIgnore HttpStatus httpStatus
) {

    public static <T> ApiResponse<T> onSuccess(BaseResponse successCode, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .code(successCode.getCode())
                .message(successCode.getMessage())
                .result(result)
                .httpStatus(successCode.getHttpStatus())
                .build();
    }

    public static ApiResponse<Void> onSuccess(BaseResponse successCode) {
        return ApiResponse.<Void>builder()
                .isSuccess(true)
                .code(successCode.getCode())
                .message(successCode.getMessage())
                .result(null)
                .httpStatus(successCode.getHttpStatus())
                .build();
    }

    public static ApiResponse<Void> onFailure(ErrorCode errorCode) {
        return ApiResponse.<Void>builder()
                .isSuccess(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .result(null)
                .httpStatus(errorCode.getHttpStatus())
                .build();
    }
}

package com.yeogido.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralErrorCode implements ErrorCode {

    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "COMMON4001",
            "잘못된 요청입니다."
    ),

    INVALID_PARAMETER(
            HttpStatus.BAD_REQUEST,
            "COMMON4002",
            "요청 파라미터가 올바르지 않습니다."
    ),

    INVALID_JSON(
            HttpStatus.BAD_REQUEST,
            "COMMON4003",
            "요청 형식이 올바르지 않습니다."
    ),

    REQUIRED_FIELD_MISSING(
            HttpStatus.BAD_REQUEST,
            "COMMON4004",
            "필수 입력값이 누락되었습니다."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMON4031",
            "접근 권한이 없습니다."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON5001",
            "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
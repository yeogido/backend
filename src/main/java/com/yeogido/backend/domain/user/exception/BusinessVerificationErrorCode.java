package com.yeogido.backend.domain.user.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BusinessVerificationErrorCode implements ErrorCode {

    INVALID_BUSINESS_NUMBER(
            HttpStatus.BAD_REQUEST,
            "BUSINESS_VERIFY4001",
            "사업자 등록번호 형식이 올바르지 않습니다"
    ),

    BUSINESS_VERIFICATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "BUSINESS_VERIFY4002",
            "입력한 사업자 정보가 국세청 등록 정보와 일치하지 않습니다"
    ),

    BUSINESS_NOT_ACTIVE(
            HttpStatus.NOT_FOUND,
            "BUSINESS_VERIFY4041",
            "폐업 상태이거나 존재하지 않는 사업자 번호입니다"
    ),

    BUSINESS_NUMBER_DUPLICATED(
            HttpStatus.CONFLICT,
            "BUSINESS_VERIFY4091",
            "이미 등록된 사업자 번호입니다"
    ),

    BUSINESS_VERIFICATION_API_ERROR(
            HttpStatus.SERVICE_UNAVAILABLE,
            "BUSINESS_VERIFY5001",
            "국세청 외부 연동 중 오류가 발생했습니다"
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
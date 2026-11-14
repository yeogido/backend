package com.yeogido.backend.domain.user.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BusinessVerificationErrorCode implements ErrorCode {

    BUSINESS_VERIFICATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "BUSINESS_VERIFY4002",
            "입력한 사업자 정보가 국세청 등록 정보와 일치하지 않습니다"
    ),

    BUSINESS_NOT_REGISTERED(
            HttpStatus.NOT_FOUND,
            "BUSINESS_VERIFY4041",
            "국세청에 등록되지 않은 사업자 번호예요. 번호를 다시 확인해 주세요"
    ),

    BUSINESS_SUSPENDED(
            HttpStatus.NOT_FOUND,
            "BUSINESS_VERIFY4042",
            "휴업 중인 사업자는 인증할 수 없어요. 영업을 재개한 뒤 다시 인증해 주세요"
    ),

    BUSINESS_CLOSED(
            HttpStatus.NOT_FOUND,
            "BUSINESS_VERIFY4043",
            "폐업한 사업자 번호는 인증할 수 없어요"
    ),

    BUSINESS_NUMBER_DUPLICATED(
            HttpStatus.CONFLICT,
            "BUSINESS_VERIFY4091",
            "이미 등록된 사업자 번호입니다"
    ),

    BUSINESS_PLACE_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "BUSINESS_VERIFY4092",
            "이미 다른 사업자 인증에 연결된 장소입니다. 선택한 사업장이 맞는지 확인해 주세요"
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
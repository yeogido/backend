package com.yeogido.backend.domain.business.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BusinessPromotionErrorCode implements ErrorCode {

    BUSINESS_PROMOTION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "BUSINESS_PROMOTION4091",
            "이미 해당 장소에 등록된 홍보글이 있습니다"
    ),

    BUSINESS_PROMOTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "BUSINESS_PROMOTION4041",
            "소상공인 홍보글을 찾을 수 없습니다"
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

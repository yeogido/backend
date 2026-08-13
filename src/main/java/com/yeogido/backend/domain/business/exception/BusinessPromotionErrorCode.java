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
    ),

    BUSINESS_PROMOTION_VERIFICATION_REQUIRED(
            HttpStatus.FORBIDDEN,
            "BUSINESS_PROMOTION4031",
            "승인된 본인 사업장만 홍보글을 등록할 수 있습니다"
    ),

    BUSINESS_PROMOTION_FORBIDDEN_UPDATE(
            HttpStatus.FORBIDDEN,
            "BUSINESS_PROMOTION4032",
            "해당 홍보글을 수정할 권한이 없습니다"
    ),

    BUSINESS_PROMOTION_FORBIDDEN_DELETE(
            HttpStatus.FORBIDDEN,
            "BUSINESS_PROMOTION4033",
            "해당 홍보글을 삭제할 권한이 없습니다"
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

package com.yeogido.backend.domain.travel.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StickerErrorCode implements ErrorCode {

    INVALID_STICKER_IMAGE_KEY(
            HttpStatus.BAD_REQUEST,
            "STICKER4001",
            "올바르지 않은 스티커 이미지 키입니다."
    ),

    DEFAULT_STICKER_DELETE_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "STICKER4002",
            "기본 제공 스티커는 삭제할 수 없습니다."
    ),

    STICKER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "STICKER4041",
            "스티커 정보를 찾을 수 없습니다."
    ),

    CUSTOM_STICKER_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "STICKER4091",
            "커스텀 스티커는 최대 10개까지 등록할 수 있습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

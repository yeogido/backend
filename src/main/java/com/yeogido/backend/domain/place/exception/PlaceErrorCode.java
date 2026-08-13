package com.yeogido.backend.domain.place.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor

public enum PlaceErrorCode implements ErrorCode {

    PLACE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PLACE4041",
            "장소를 찾을 수 없습니다"
    ),

    INVALID_PLACE_LIKE_SOURCE(
            HttpStatus.BAD_REQUEST,
            "PLACE4001",
            "유효하지 않은 장소 좋아요 출처입니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

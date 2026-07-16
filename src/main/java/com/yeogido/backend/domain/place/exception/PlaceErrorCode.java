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

    PLACE_LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PLACE4042",
            "장소 좋아요 정보를 찾을 수 없습니다"
    ),

    PLACE_LIKE_ALREADY_EXIST(
            HttpStatus.CONFLICT,
            "PLACE4091",
            "이미 좋아요를 누른 장소입니다"
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

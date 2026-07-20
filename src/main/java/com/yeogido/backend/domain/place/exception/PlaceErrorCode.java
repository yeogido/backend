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
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

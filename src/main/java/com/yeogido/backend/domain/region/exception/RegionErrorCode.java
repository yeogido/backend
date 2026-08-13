package com.yeogido.backend.domain.region.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RegionErrorCode implements ErrorCode {

    REGION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REGION4041",
            "지역이 존재하지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

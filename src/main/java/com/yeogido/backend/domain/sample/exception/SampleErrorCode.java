package com.yeogido.backend.domain.sample.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SampleErrorCode implements ErrorCode {

    SAMPLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SAMPLE4041",
            "샘플을 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
package com.yeogido.backend.domain.content.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ContentErrorCode implements ErrorCode {

    CONTENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CONTENT4041",
            "콘텐츠가 존재하지 않습니다."
    );


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
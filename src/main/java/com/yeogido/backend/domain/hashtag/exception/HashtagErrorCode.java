package com.yeogido.backend.domain.hashtag.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HashtagErrorCode implements ErrorCode {

    HASHTAG_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "HASHTAG4041",
            "해시태그가 존재하지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
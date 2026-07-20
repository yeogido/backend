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
    ),

    CONTENT_LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CONTENT_LIKE_NOT_FOUND",
            "좋아요 정보를 찾을 수 없습니다."
    ),

    CONTENT_ALREADY_DELETE(
            HttpStatus.CONFLICT,
            "CONTENT4091",
                    "이미 삭제된 문화콘텐츠입니다."
    ),

    CONTENT_ALREADY_LIKED(
            HttpStatus.CONFLICT,
            "CONTENT4092",
            "이미 좋아요를 누른 문화콘텐츠입니다."
    );
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
package com.yeogido.backend.domain.review.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    DUPLICATE_IMAGE_ORDER(
            HttpStatus.BAD_REQUEST,
            "REVIEW4001",
            "리뷰 이미지 순서에 중복이 존재합니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

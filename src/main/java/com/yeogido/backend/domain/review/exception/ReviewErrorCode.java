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
    ),

    INVALID_IMAGE_ORDER(
            HttpStatus.BAD_REQUEST,
            "REVIEW4002",
            "리뷰 이미지 순서는 1부터 연속된 값이어야 합니다."
    ),

    REVIEW_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REVIEW4031",
            "본인이 작성한 리뷰만 수정할 수 있습니다."
    ),

    REVIEW_DELETE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "REVIEW4032",
            "본인이 작성한 리뷰만 삭제할 수 있습니다."
    ),

    REVIEW_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REVIEW4041",
            "리뷰를 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

package com.yeogido.backend.domain.course.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CourseErrorCode implements ErrorCode {

    COURSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COURSE4041",
            "추천 코스가 존재하지 않습니다."
    ),

    DUPLICATE_HASHTAG(
            HttpStatus.BAD_REQUEST,
            "COURSE4001",
            "중복된 해시태그가 포함되어 있습니다."
    ),

    TOO_MANY_HASHTAGS(
            HttpStatus.BAD_REQUEST,
            "COURSE4002",
            "해시태그는 최대 5개까지 등록할 수 있습니다."
    ),

    COURSE_ITEM_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "COURSE4003",
            "코스 구성 항목은 최소 1개 이상 필요합니다."
    ),

    PLACE_ITEM_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "COURSE4004",
            "코스에는 장소가 최소 1개 이상 포함되어야 합니다."
    ),

    DUPLICATE_COURSE_ITEM_ORDER(
            HttpStatus.BAD_REQUEST,
            "COURSE4005",
            "코스 구성 항목 순서는 중복될 수 없습니다."
    ),

    INVALID_COURSE_ITEM(
            HttpStatus.BAD_REQUEST,
            "COURSE4006",
            "코스 구성 항목이 올바르지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
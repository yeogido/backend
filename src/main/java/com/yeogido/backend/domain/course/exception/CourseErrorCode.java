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
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

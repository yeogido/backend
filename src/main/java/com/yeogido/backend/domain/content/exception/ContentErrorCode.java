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
    INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "CONTENT4002",
            "행사 시작일은 종료일보다 늦을 수 없습니다."
    ),
    TOUR_API_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "CONTENT5031",
            "한국관광공사 API 인증 정보가 설정되지 않았습니다."
    ),
    TOUR_API_ERROR(
            HttpStatus.BAD_GATEWAY,
            "CONTENT5021",
            "한국관광공사 API 호출에 실패했습니다."
    ),
    TOUR_CONTENT_SYNC_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CONTENT5001",
            "한국관광공사 콘텐츠를 DB에 반영하지 못했습니다. 지역 데이터와 서버 로그를 확인해주세요."
    ),
    TOUR_CONTENT_DUPLICATED(
            HttpStatus.CONFLICT,
            "CONTENT4091",
            "이미 등록된 한국관광공사 콘텐츠입니다."
    ),
    TOUR_CONTENT_NOT_PENDING(
            HttpStatus.CONFLICT,
            "CONTENT4092",
            "관리자 확인 대기 중인 관광공사 콘텐츠가 아닙니다."
    );


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

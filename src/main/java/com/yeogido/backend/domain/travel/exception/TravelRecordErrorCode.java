package com.yeogido.backend.domain.travel.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TravelRecordErrorCode implements ErrorCode {

    IMAGE_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4001",
            "여행 기록 사진은 최소 1장 이상 등록해야 합니다."
    ),

    IMAGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4002",
            "여행 기록 사진은 최대 5장까지 등록할 수 있습니다."
    ),

    INVALID_STICKER_POSITION(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4003",
            "스티커 위치 정보가 올바르지 않습니다."
    ),

    INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4004",
            "여행 기간 정보가 올바르지 않습니다."
    ),

    INVALID_IMAGE_KEY(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4005",
            "이미지 key 정보가 올바르지 않습니다."
    ),

    INVALID_PAGE_REQUEST(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4006",
            "페이지 요청 값이 올바르지 않습니다."
    ),

    INVALID_IMAGE_ORDER(
            HttpStatus.BAD_REQUEST,
            "TRAVEL_RECORD4007",
            "사진 순서 정보가 올바르지 않습니다."
    ),

    UPDATE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "TRAVEL_RECORD4031",
            "해당 여행 기록을 수정할 권한이 없습니다."
    ),

    DELETE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "TRAVEL_RECORD4032",
            "해당 여행 기록을 삭제할 권한이 없습니다."
    ),

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "TRAVEL_RECORD4033",
            "해당 여행 기록에 접근할 권한이 없습니다."
    ),

    TRAVEL_RECORD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TRAVEL_RECORD4041",
            "여행 기록을 찾을 수 없습니다."
    ),

    STICKER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "STICKER4042",
            "선택한 스티커를 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

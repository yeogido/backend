package com.yeogido.backend.domain.placeenrichment.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceEnrichmentErrorCode implements ErrorCode {

    PLACE_NAME_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "PLACE_ENRICHMENT4001",
            "장소명은 필수입니다."
    ),
    INVALID_TOUR_PLACE_SEARCH_CONFIG(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PLACE_ENRICHMENT5001",
            "관광공사 장소 검색 설정이 올바르지 않습니다."
    ),
    ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PLACE_ENRICHMENT5002",
            "장소 정보 보강 입력과 출력 파일은 달라야 합니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

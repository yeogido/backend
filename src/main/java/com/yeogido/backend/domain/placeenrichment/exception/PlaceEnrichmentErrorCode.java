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
    GEMINI_ENRICHMENT_INPUT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "PLACE_ENRICHMENT4002",
            "Gemini 장소 보완에 필요한 장소 정보가 누락되었습니다."
    ),
    INVALID_ENRICHMENT_CONFIG(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PLACE_ENRICHMENT5003",
            "장소 정보 보완 설정이 올바르지 않습니다."
    ),
    DAUM_SEARCH_API_ERROR(
            HttpStatus.BAD_GATEWAY,
            "PLACE_ENRICHMENT5021",
            "Daum 장소 검색에 실패했습니다."
    ),
    GEMINI_GENERATION_API_ERROR(
            HttpStatus.BAD_GATEWAY,
            "PLACE_ENRICHMENT5022",
            "Gemini 장소 설명 생성에 실패했습니다."
    ),
    INVALID_ENRICHMENT_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "PLACE_ENRICHMENT5023",
            "장소 정보 보완 응답을 검증할 수 없습니다."
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

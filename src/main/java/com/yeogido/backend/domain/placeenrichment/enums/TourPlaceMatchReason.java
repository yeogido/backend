package com.yeogido.backend.domain.placeenrichment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TourPlaceMatchReason {
    NAME_AND_LOCATION_MATCHED("이름과 위치 일치"),
    MATCHED_WITHOUT_OVERVIEW("장소 매칭 완료, overview 없음"),
    MULTIPLE_MATCHES("이름과 위치가 일치하는 후보가 여러 개 존재"),
    INSUFFICIENT_EVIDENCE("후보는 있으나 동일 장소로 확정할 근거 부족"),
    NO_MATCH("일치하는 장소 없음"),
    SEARCH_LIMIT_REACHED("검색 조회 상한 도달로 매칭 확인 미완료");

    private final String description;
}

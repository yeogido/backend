package com.yeogido.backend.domain.placeenrichment.dto.request;

import java.math.BigDecimal;

public class TourPlaceRequest {

    // phone과 officialUrl에 검색 모델 생성값 대신 호출자가 확보한 장소 식별 정보 사용
    // 공용 브랜드 홈페이지 대신 해당 장소나 지점의 URL 전달
    public record Input(
            String externalPlaceId,
            String name,
            Long regionId,
            String categoryGroupCode,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String officialUrl
    ) {
        public Input(String externalPlaceId, String name, Long regionId, String categoryGroupCode,
                     String roadAddress, String lotAddress, BigDecimal latitude, BigDecimal longitude) {
            this(externalPlaceId, name, regionId, categoryGroupCode, roadAddress, lotAddress,
                    latitude, longitude, null, null);
        }
    }
}

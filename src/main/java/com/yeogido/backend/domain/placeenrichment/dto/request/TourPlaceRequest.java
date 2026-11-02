package com.yeogido.backend.domain.placeenrichment.dto.request;

import java.math.BigDecimal;

public class TourPlaceRequest {

    // phone과 officialUrl은 검색 모델이 생성한 값이 아닌, 호출자가 확보한 장소 식별 정보다.
    // 공용 브랜드 홈페이지 대신 해당 장소·지점의 URL을 전달한다.
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

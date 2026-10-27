package com.yeogido.backend.domain.placeenrichment.dto.request;

import java.math.BigDecimal;

public class TourPlaceReqDTO {

    public record Input(
            String externalPlaceId,
            String name,
            Long regionId,
            String categoryGroupCode,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}

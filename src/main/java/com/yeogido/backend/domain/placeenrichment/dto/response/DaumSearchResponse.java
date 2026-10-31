package com.yeogido.backend.domain.placeenrichment.dto.response;

import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;

public class DaumSearchResponse {

    public record SearchSource(
            String sourceId,
            String url,
            PlaceEvidenceSourceType sourceType,
            String title,
            String snippet
    ) {
    }
}

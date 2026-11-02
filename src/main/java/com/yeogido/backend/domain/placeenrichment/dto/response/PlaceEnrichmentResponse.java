package com.yeogido.backend.domain.placeenrichment.dto.response;

import com.yeogido.backend.domain.placeenrichment.enums.PlaceEnrichmentStatus;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;

public class PlaceEnrichmentResponse {

    public record Result(
            String externalPlaceId,
            String name,
            String overview,
            PlaceOverviewSource overviewSource,
            PlaceEnrichmentStatus status,
            TourPlaceResponse.MatchResult tourResult,
            GeminiPlaceResponse.Result geminiResult
    ) {
    }

}

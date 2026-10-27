package com.yeogido.backend.domain.placeenrichment.dto.response;

import com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchReason;
import com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus;

import java.util.List;

public class TourPlaceResDTO {

    public record MatchResult(
            String externalPlaceId,
            TourPlaceMatchStatus matchStatus,
            TourPlaceMatchReason matchReason,
            String contentId,
            String overview,
            int searchRequestCount,
            boolean searchLimitReached,
            List<Candidate> candidates
    ) {
    }

    public record Candidate(
            String contentId,
            String name,
            String address,
            Double distanceMeters,
            boolean nameMatched,
            boolean addressMatched,
            boolean strongMatch
    ) {
    }
}

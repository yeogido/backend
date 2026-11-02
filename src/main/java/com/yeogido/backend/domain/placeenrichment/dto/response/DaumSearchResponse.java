package com.yeogido.backend.domain.placeenrichment.dto.response;

import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class DaumSearchResponse {

    public record SearchSource(
            String sourceId,
            String url,
            PlaceEvidenceSourceType sourceType,
            String title,
            String snippet,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant collectedAt
    ) {
        public SearchSource(String sourceId, String url, PlaceEvidenceSourceType sourceType,
                            String title, String snippet) {
            this(sourceId, url, sourceType, title, snippet, null);
        }
    }
}

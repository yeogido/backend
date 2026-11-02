package com.yeogido.backend.domain.placeenrichment.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import com.yeogido.backend.domain.placeenrichment.enums.GeminiPlaceMatchStatus;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceFactCategory;

import java.util.List;

public final class GeminiPlaceResponse {

    private GeminiPlaceResponse() { }

    public record Result(
            String id,
            String name,
            GeminiPlaceMatchStatus matchStatus,
            String matchReason,
            String overview,
            List<Source> sources,
            List<SentenceSource> sentenceSources,
            List<Conflict> conflicts,
            JsonNode groundingMetadata,
            List<Fact> facts
    ) {
    }

    public record Fact(
            PlaceFactCategory category,
            String value,
            String scope,
            String evidence,
            String sourceId
    ) {
    }

    public record Source(
            String sourceId,
            String url,
            PlaceEvidenceSourceType sourceType,
            String evidence,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant collectedAt
    ) {
    }

    public record SentenceSource(
            String sentence,
            List<String> sourceIds
    ) {
    }

    public record Conflict(
            String field,
            String description,
            List<String> sourceIds
    ) {
    }

    public record SelectedFact(
            PlaceFactCategory category,
            String value,
            String scope,
            String evidenceId,
            String subject
    ) {
    }

    public record SelectedSentence(
            String sentence,
            List<String> evidenceIds
    ) {
    }

    public record SelectedConflict(
            String field,
            String description,
            List<String> evidenceIds
    ) {
    }

    public record Selection(
            String id,
            String name,
            GeminiPlaceMatchStatus matchStatus,
            String matchReason,
            String overview,
            List<String> matchEvidenceIds,
            List<SelectedSentence> sentenceSources,
            List<SelectedConflict> conflicts,
            List<SelectedFact> facts
    ) {
    }
}

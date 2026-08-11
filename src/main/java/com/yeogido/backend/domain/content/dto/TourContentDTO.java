package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class TourContentDTO {

    public record SearchRequest(
            @NotBlank
            @Schema(description = "검색어", example = "부산 불꽃축제")
            String keyword,

            @Schema(description = "관광공사 지역 코드")
            Integer areaCode,

            @Schema(description = "페이지 번호", defaultValue = "1")
            @Positive
            Integer page,

            @Schema(description = "페이지 크기", defaultValue = "10")
            @Positive
            Integer size
    ) {
    }

    public record SearchResponse(
            int page,
            int size,
            int totalCount,
            List<SearchItem> items
    ) {
    }

    public record SearchItem(
            String contentId,
            String contentTypeId,
            String title,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String thumbnailImageUrl
    ) {
    }

    public record DetailResponse(
            String externalContentId,
            String contentTypeId,
            String title,
            String description,
            ContentCategory category,
            LocalDate startDate,
            LocalDate endDate,
            String contactPhone,
            String officialUrl,
            String thumbnailImageUrl,
            List<String> imageUrls,
            Place place
    ) {
    }

    public record Place(
            String externalPlaceId,
            PlaceSource source,
            String name,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record ImageImportRequest(
            @NotBlank
            @Schema(description = "한국관광공사 이미지 URL")
            String imageUrl
    ) {
    }

    public record ImageImportResponse(
            String imageKey,
            String imageUrl
    ) {
    }
}

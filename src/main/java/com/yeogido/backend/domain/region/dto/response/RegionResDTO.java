package com.yeogido.backend.domain.region.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

public class RegionResDTO {

    @Builder
    @Schema(name = "RegionPreview", description = "상위 지역 미리보기 응답")
    public record RegionPreview(
            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "지역명", example = "서울")
            String name,

            @Schema(description = "지역 대표 이미지 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/regions/seoul.jpg")
            String imageUrl
    ) { }

    @Builder
    @Schema(name = "RegionListResponse", description = "상위 지역 목록 조회 응답")
    public record RegionListResponse(
            @Schema(description = "상위 지역 목록")
            List<RegionPreview> regions
    ) { }

    @Builder
    @Schema(name = "RegionDetailResponse", description = "지역 상세 조회 응답")
    public record RegionDetailRes(
            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "지역명", example = "서울")
            String name,

            @Schema(description = "전체 지역명", example = "서울특별시")
            String fullName,

            @Schema(description = "지역 소개글", nullable = true, example = "전통과 현대가 어우러진 대한민국의 수도입니다.")
            String description,

            @Schema(description = "지역 대표 이미지 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/regions/seoul.jpg")
            String imageUrl
    ) { }

    @Builder
    @Schema(name = "RegionSearchResponse", description = "지역 검색 응답")
    public record RegionSearchRes(
            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "지역명", example = "강남구")
            String name,

            @Schema(description = "전체 지역명", example = "서울특별시 강남구")
            String fullName
    ) { }

    @Builder
    @Schema(name = "SubRegionPreview", description = "하위 지역 미리보기 응답")
    public record SubRegionPreview(
            @Schema(description = "하위 지역 ID", example = "1")
            Long subRegionId,

            @Schema(description = "하위 지역명", example = "강남구")
            String name
    ) { }

    @Builder
    @Schema(name = "SubRegionListResponse", description = "하위 지역 목록 조회 응답")
    public record SubRegionListResponse(
            @Schema(description = "하위 지역 목록")
            List<SubRegionPreview> subRegions
    ) { }

    @Builder
    @Schema(name = "PopularRegionResponse", description = "인기 지역 조회 응답")
    public record PopularRegionRes(
            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "지역명", example = "전주")
            String name,

            @Schema(description = "전체 지역명", example = "전라북도")
            String fullName,

            @Schema(description = "지역 대표 이미지 URL", example = "https://example.com/regions/jeonju.jpg")
            String imageUrl
    ) { }
}

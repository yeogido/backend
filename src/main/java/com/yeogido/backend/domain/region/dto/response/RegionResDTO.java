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

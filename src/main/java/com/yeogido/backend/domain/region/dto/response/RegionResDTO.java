package com.yeogido.backend.domain.region.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public class RegionResDTO {

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

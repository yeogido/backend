package com.yeogido.backend.domain.place.dto.request;

import com.yeogido.backend.domain.place.enums.PlaceLikeSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "장소 좋아요 등록 요청")
public record PlaceLikeRequest(

        @NotNull
        @Schema(
                description = "좋아요 발생 출처 (COURSE_ITEM, CONTENT, PROMOTION)",
                example = "CONTENT"
        )
        PlaceLikeSourceType sourceType,

        @NotNull
        @Positive
        @Schema(
                description = "좋아요 발생 출처 ID. CONTENT인 경우 문화콘텐츠 ID",
                example = "1"
        )
        Long sourceId
) {
}

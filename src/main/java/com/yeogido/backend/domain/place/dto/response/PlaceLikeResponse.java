package com.yeogido.backend.domain.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public class PlaceLikeResponse {
    @Builder
    @Schema(name = "PlaceLikeCreateResponse", description = "장소 좋아요 등록 응답")
    public record Create(
            @Schema(description = "장소ID", example = "1")
            Long placeId,

            @Schema(description = "좋아요 여부", example = "true")
            Boolean isLiked
    ) { }

    @Builder
    @Schema(name = "PlaceLikeDeleteResponse", description = "장소 좋아요 취소 응답")
    public record Delete(
            @Schema(description = "장소ID", example = "1")
            Long placeId,

            @Schema(description = "좋아요 여부", example = "false")
            Boolean isLiked
    ){ }
}
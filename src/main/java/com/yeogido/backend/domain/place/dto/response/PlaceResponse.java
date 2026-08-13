package com.yeogido.backend.domain.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public class PlaceResponse {
    @Builder
    @Schema(name = "PlaceLikeResponse", description = "장소 좋아요 등록 및 취소 응답")
    public record PlaceLikeRes(

            @Schema(description = "좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "좋아요 수", example = "10")
            Long likeCount
    ){ }
}
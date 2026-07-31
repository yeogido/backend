package com.yeogido.backend.domain.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public class StickerResDTO {

    @Schema(name = "StickerListResponse", description = "여행 기록 스티커 목록 조회 응답")
    public record StickerListResponse(
            @Schema(description = "스티커 카테고리 목록")
            List<StickerCategoryResponse> categories
    ) {
    }

    @Schema(name = "StickerCategoryResponse", description = "카테고리별 스티커 목록 응답")
    public record StickerCategoryResponse(
            @Schema(description = "스티커 카테고리", example = "NATURE")
            String category,

            @Schema(description = "해당 카테고리에 포함된 스티커 목록")
            List<StickerResponse> stickers
    ) {
    }

    @Schema(name = "StickerResponse", description = "스티커 응답")
    public record StickerResponse(
            @Schema(description = "스티커 ID", example = "1")
            Long stickerId,

            @Schema(description = "스티커 이름", example = "해")
            String name,

            @Schema(description = "스티커 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/stickers/default/nature/sun.png")
            String imageUrl,

            @Schema(description = "스티커 유형", example = "DEFAULT")
            String stickerType
    ) {
    }
}

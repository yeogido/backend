package com.yeogido.backend.domain.travel.dto.response;

import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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
            StickerCategory category,

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
            StickerType stickerType
    ) {
    }

    @Schema(name = "CustomStickerCreateResponse", description = "커스텀 스티커 등록 응답")
    public record CreateResponse(
            @Schema(description = "등록된 커스텀 스티커 ID", example = "51")
            Long stickerId,

            @Schema(description = "커스텀 스티커 이름", example = "나만의 스티커 1")
            String name,

            @Schema(description = "커스텀 스티커 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/stickers/550e8400-e29b-41d4-a716-446655440000.png")
            String imageUrl,

            @Schema(description = "스티커 카테고리", example = "CUSTOM")
            StickerCategory category,

            @Schema(description = "스티커 유형", example = "CUSTOM")
            StickerType stickerType,

            @Schema(description = "커스텀 스티커 등록 일시", example = "2026-07-24T14:30:00")
            LocalDateTime createdAt
    ) {
    }
}

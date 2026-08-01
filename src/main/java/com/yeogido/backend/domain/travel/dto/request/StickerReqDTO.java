package com.yeogido.backend.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class StickerReqDTO {

    @Schema(name = "CustomStickerCreateRequest", description = "커스텀 스티커 등록 요청")
    public record CreateRequest(
            @Schema(description = "Presigned URL로 업로드한 temp 경로 이미지 key", example = "temp/550e8400-e29b-41d4-a716-446655440000.png")
            @NotBlank(message = "스티커 이미지 key는 필수입니다.")
            String imageKey
    ) {
    }
}

package com.yeogido.backend.domain.file.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public class FileResDTO {

    @Builder
    @Schema(name = "PresignedUrlResponse", description = "Presigned URL 발급 응답")
    public record PresignedUrlRes(
            @Schema(
                    description = "파일 업로드용 Presigned URL",
                    example = "https://bucket.s3.ap-northeast-2.amazonaws.com/temp/8e1f1d4c-1d2f-4f2d-a7b2-9c3d4e5f6a7b.jpg"
            )
            String uploadUrl,

            @Schema(description = "업로드 이미지 키", example = "temp/8e1f1d4c-1d2f-4f2d-a7b2-9c3d4e5f6a7b.jpg")
            String objectKey
    ) { }
}

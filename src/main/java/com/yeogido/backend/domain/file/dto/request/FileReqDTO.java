package com.yeogido.backend.domain.file.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public class FileReqDTO {

    @Schema(name = "PresignedUrlRequest", description = "Presigned URL 발급 요청")
    public record PresignedUrlReq(
            @Schema(description = "업로드 디렉터리", example = "courses")
            String directory,

            @Schema(description = "업로드 파일명", example = "thumbnail.jpg")
            String fileName,

            @Schema(description = "파일 Content-Type", example = "image/jpeg")
            String contentType
    ) { }
}

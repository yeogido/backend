package com.yeogido.backend.domain.file.dto.request;

import com.yeogido.backend.domain.file.enums.ImageDirectory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FileReqDTO {

    @Schema(name = "PresignedUrlRequest", description = "Presigned URL 발급 요청")
    public record PresignedUrlReq(
            @NotNull(message = "업로드 디렉터리는 필수입니다")
            @Schema(description = "업로드 디렉터리", example = "COURSE")
            ImageDirectory directory,

            @NotBlank(message = "파일명은 필수입니다")
            @Schema(description = "업로드 파일명", example = "thumbnail.jpg")
            String fileName,

            @NotBlank(message = "Content-Type은 필수입니다")
            @Schema(description = "파일 Content-Type", example = "image/jpeg")
            String contentType
    ) { }
}

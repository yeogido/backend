package com.yeogido.backend.domain.hashtag.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public class HashtagResDTO {

    @Builder
    @Schema(name = "HashtagResponse", description = "해시태그 목록 조회 응답")
    public record HashtagRes(
            @Schema(description = "해시태그 ID", example = "1")
            Long id,

            @Schema(description = "해시태그명", example = "자연")
            String name
    ) { }
}

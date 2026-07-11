package com.yeogido.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class UserResDTO {
    public record LikedResponse(

            @Schema(description = "콘텐츠 ID")
            Long id,

            @Schema(description = "카테고리")
            String category,

            @Schema(description = "제목")
            String title,

            @Schema(description = "대표사진 URL")
            String thumbnailUrl,

            @Schema(description = "소요 시간")
            String duration,

            @Schema(description = "위치")
            String location,

            @Schema(description = "해시태그 ID")
            String hashtagIds,

            @Schema(description = "생성일")
            String createdAt

    ){}
}

package com.yeogido.backend.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public class ContentReqDTO {
    public record ContentListReq(

            @Schema(description = "지역 ID")
            Long regionId,

            @Schema(description = "카테고리")
            String category,

            @Schema(description = "검색어")
            String keyword,

            @Schema(description = "정렬 기준")
            String sort,

            @Schema(description = "커서")
            Long cursor,

            @Schema(description = "조회 개수")
            Integer size

    ){}

    public record ContentCreateReq(

            @Schema(description = "장소 ID")
            Long placeId,

            @Schema(description = "문화콘텐츠명")
            String title,

            @Schema(description = "문화콘텐츠 설명")
            String description,

            @Schema(description = "카테고리")
            String category,

            @Schema(description = "행사 시작일")
            LocalDate startDate,

            @Schema(description = "행사 종료일")
            LocalDate endDate,

            @Schema(description = "문의 연락처")
            String contactPhone,

            @Schema(description = "대표 사진 Key")
            String thumbnailImageKey,

            @Schema(description = "해시태그 ID 목록")
            List<String> hashtagIds

    ){}


}

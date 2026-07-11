package com.yeogido.backend.domain.course.dto.response;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class CourseResDTO {

    @Schema(name = "CoursePreviewResponse", description = "추천 코스 목록 정보")
    public record CoursePreview(

            @Schema(description = "코스 ID", example = "1")
            Long courseId,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/course1.jpg")
            String thumbnailUrl,

            @Schema(description = "코스 제목", example = "강릉 혼자 여행 코스")
            String title,

            @Schema(description = "지역명", example = "강릉")
            String region,

            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType,

            @Schema(description = "동행 유형", example = "SOLO")
            CompanionType companionType,

            @Schema(description = "태그", example = "[\"여름\", \"자연\", \"바다\"]")
            List<String> tags,

            @Schema(description = "현재 사용자의 코스 좋아요 여부", example = "true")
            Boolean isLiked
    ) { }
}

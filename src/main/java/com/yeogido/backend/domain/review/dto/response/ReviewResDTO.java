package com.yeogido.backend.domain.review.dto.response;

import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewResDTO {

    @Schema(name = "RecentReviewsResponse", description = "최근 후기 조회 응답")
    public record RecentReviewsResponse(
            @Schema(description = "최근 후기 목록")
            List<RecentReview> reviews
    ) {
    }

    @Schema(name = "RecentReview", description = "최근 후기 정보")
    public record RecentReview(
            @Schema(description = "리뷰 ID", example = "101")
            Long reviewId,

            @Schema(description = "리뷰 내용", example = "지도 동선이 너무 편했어요.")
            String content,

            @Schema(description = "별점", example = "5.0")
            BigDecimal rating,

            @Schema(description = "생성 일시", example = "2026-07-05T15:30:00")
            LocalDateTime createdAt,

            @Schema(description = "작성자 정보")
            Author author
    ) {
    }

    @Schema(name = "ReviewDetail", description = "최근 후기 목록 아이템")
    public record ReviewDetail(
            @Schema(description = "리뷰 ID", example = "101")
            Long reviewId,

            @Schema(description = "리뷰 내용", example = "지도 동선이 너무 편했어요.")
            String content,

            @Schema(description = "별점", example = "5.0")
            BigDecimal rating,

            @Schema(description = "생성 일시", example = "2026-07-05T15:30:00")
            LocalDateTime createdAt,

            @Schema(description = "작성자 정보")
            Author author,

            @Schema(description = "추천 코스 정보")
            Course course
    ) {
    }

    @Schema(name = "ReviewAuthor", description = "리뷰 작성자 정보")
    public record Author(
            @Schema(description = "닉네임", example = "민지")
            String nickname,

            @Schema(description = "연령대", example = "TWENTIES")
            String ageGroup,

            @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
            String profileImageUrl
    ) {
    }

    @Schema(name = "ReviewCourse", description = "리뷰 추천 코스 정보")
    public record Course(
            @Schema(description = "추천 코스 ID", example = "15")
            Long courseId,

            @Schema(description = "추천 코스 제목", example = "강릉 혼자 여행 코스")
            String title,

            @Schema(description = "썸네일 URL", example = "https://example.com/course.png")
            String thumbnailUrl,

            @Schema(description = "여행 기간 타입", example = "TWO_NIGHT")
            DurationType durationType,

            @Schema(description = "이동 수단 타입", example = "PUBLIC")
            TransportType transportType,

            @Schema(description = "좋아요 여부", example = "false")
            Boolean isLiked
    ) {
    }
}

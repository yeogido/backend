package com.yeogido.backend.domain.user.dto;

import com.yeogido.backend.domain.user.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class UserResDTO {

    public record LikedResponse(
            @Schema(description = "콘텐츠 ID")
            Long id,

            @Schema(description = "카테고리")
            LikeCategory category,

            @Schema(description = "제목")
            String title,

            @Schema(description = "여러출처 장소ID")
            String externalPlaceId,

            @Schema(description = "대표 사진")
            String thumbnailImage,

            @Schema(description = "여행 기간")
            String duration,

            @Schema(description = "행사 시작일")
            LocalDate startDate,

            @Schema(description = "행사 종료일")
            LocalDate endDate,

            @Schema(description = "위치")
            String location,

            @Schema(description = "현위치와의 거리")
            Double distance,

            @Schema(description = "해시태그")
            List<String> hashtags,

            @Schema(description = "좋아요 등록 시각")
            String likedAt
    ) {}

    @Schema(name = "UserProfileResponse", description = "내 프로필 조회 응답")
    public record Profile(
            @Schema(description = "사용자 ID", example = "1")
            Long userId,

            @Schema(description = "이메일", example = "abc@example.com")
            String email,

            @Schema(description = "이름", example = "홍길동")
            String name,

            @Schema(description = "지역", example = "서울")
            String region,

            @Schema(description = "출생연도", example = "2000")
            String birthYear,

            @Schema(description = "사용자 권한", example = "USER")
            UserRole role,

            @Schema(description = "프로필 이미지 URL", example = "https://s3.ap-northeast-2.amazonaws.com/.../profile.jpg")
            String profileImageUrl
    ) {}


    public record MyCourseResponse(
            @Schema(description = "게시물 ID")
            Long id,

            @Schema(description = "게시물 제목")
            String title,

            @Schema(description = "게시물 내용")
            String content,

            @Schema(description = "게시물 썸네일 이미지 URL")
            String thumbnailUrl,

            @Schema(description = "이동 수단")
            String transportType,

            @Schema(description = "동행 유형")
            String companionType,

            @Schema(description = "해시태그")
            List<String> hashtags,

            @Schema(description = "게시물 등록 일시")
            LocalDateTime createdAt
    ) {}


    public record MyReviewResponse(

            @Schema(description = "후기 ID")
            Long reviewId,

            @Schema(description = "작성자 이름")
            String reviewerName,

            @Schema(description = "작성자 프로필 이미지 URL")
            String reviewerProfileImage,

            @Schema(description = "연령대")
            AgeGroup ageGroup,

            @Schema(description = "성별")
            Gender gender,

            @Schema(description = "평점")
            Integer rating,

            @Schema(description = "후기 내용")
            String content,

            @Schema(description = "후기 작성일")
            LocalDateTime createdAt

    ) {}


    public record MyPostResponse(

            MyCourseResponse course,

            MyReviewResponse review

    ) {}
}

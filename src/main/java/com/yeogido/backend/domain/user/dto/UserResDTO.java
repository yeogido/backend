package com.yeogido.backend.domain.user.dto;

import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
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


}

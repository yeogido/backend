package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class ContentResDTO {

    @Schema(description = "문화콘텐츠 목록 정보")
    public record ContentInfo(

            @Schema(description = "문화콘텐츠 ID")
            Long contentId,

            @Schema(description = "장소 ID")
            Long placeId,

            @Schema(description = "제목")
            String title,

            @Schema(description = "썸네일 이미지")
            String thumbnailImageUrl,

            @Schema(description = "지역명")
            String regionName,

            @Schema(description = "해시태그")
            List<String> hashtags,

            @Schema(description = "좋아요 수")
            Long likeCount,

            @Schema(description = "시작일")
            LocalDate startDate,

            @Schema(description = "종료일")
            LocalDate endDate
    ) {}


    @Schema(description = "문화콘텐츠 상세 조회")
    public record ContentDetailRes(

            @Schema(description = "문화콘텐츠 ID")
            Long contentId,

            @Schema(description = "제목")
            String title,

            @Schema(description = "설명")
            String description,

            @Schema(description = "썸네일")
            String thumbnailImage,

            @Schema(description = "해시태그")
            List<String> hashtags,

            @Schema(description = "시작일")
            LocalDate startDate,

            @Schema(description = "종료일")
            LocalDate endDate,

            @Schema(description = "좋아요 여부")
            Boolean liked,

            @Schema(description = "전화번호")
            String phone,

            @Schema(description = "공식 홈페이지")
            String officialUrl,

            @Schema(description = "장소 정보")
            PlaceInfo place,

            @Schema(description = "코스 정보")
            List<CourseInfo> courses
    ) {}

    @Schema(description = "장소 정보")
    public record PlaceInfo(

            @Schema(description = "장소 ID")
            Long placeId,

            @Schema(description = "장소명")
            String name,

            @Schema(description = "도로명 주소")
            String roadAddress,

            @Schema(description = "위도")
            BigDecimal latitude,

            @Schema(description = "경도")
            BigDecimal longitude
    ) {}

    @Schema(description = "추천 코스")
    public record CourseInfo(

            @Schema(description = "코스 ID")
            Long courseId,

            @Schema(description = "제목")
            String title,

            @Schema(description = "썸네일")
            String thumbnailImage,

            @Schema(description = "설명")
            String description,

            @Schema(description = "소요 시간")
            DurationType durationType,

            @Schema(description = "이동 수단")
            TransportType transportType,

            @Schema(description = "동행 유형")
            CompanionType companionType,

            @Schema(description = "좋아요 여부")
            Boolean liked
    ) {}


    public record ContentCreateRes(
            @Schema(description = "문화콘텐츠 ID")
            Long contentId
    ) {}


    public record ContentUpdateRes(
            @Schema(description = "문화콘텐츠 ID")
            Long contentId
    ) {}

    public record ContentLikeRes(
            @Schema(description = "좋아요 여부")
            Boolean isLiked,

            @Schema(description = "좋아요 수")
            Long likeCount
    ) {}

    public record BannerRes(

            @Schema(description = "문화 콘텐츠 ID")
            Long contentId,

            @Schema(description = "문화 콘텐츠명")
            String title,

            @Schema(description = "배너 썸네일 이미지 URL")
            String thumbnailImage,

            @Schema(description = "문화 콘텐츠 설명")
            String description,

            @Schema(description = "행사 시작일")
            LocalDate startDate,

            @Schema(description = "행사 종료일")
            LocalDate endDate
    ) {}

    @Schema(name = "OngoingContentResponse", description = "홈 화면 진행 중인 행사 정보")
    public record OngoingContentRes(

            @Schema(description = "문화 콘텐츠 ID", example = "1")
            Long contentId,

            @Schema(description = "행사명", example = "양평수박축제")
            String title,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/content/watermelon-festival.jpg")
            String thumbnailImageUrl,

            @Schema(description = "행사 시작일", example = "2026-07-01")
            LocalDate startDate,

            @Schema(description = "행사 종료일", example = "2026-07-31")
            LocalDate endDate,

            @Schema(description = "시·도 및 시·군·구 지역명", example = "경기도 양평군")
            String regionName,

            @Schema(description = "해시태그", example = "[\"여름\", \"자연\", \"체험\"]")
            List<String> hashtags
    ) {}


}

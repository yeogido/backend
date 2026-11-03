package com.yeogido.backend.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentLinkType;
import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class ContentResDTO {

    @Schema(description = "월별·일별·기간별 행사 조회 결과")
    public record ContentCalendarRes(
            @Schema(description = "선택 기간과 겹치는 전체 행사 수", example = "1")
            int totalCount,
            @Schema(description = "행사 목록. 결과가 없으면 빈 배열을 반환합니다.")
            List<CalendarContentInfo> contents,
            @JsonFormat(pattern = "yyyy-MM-dd")
            @Schema(description = "실제 조회 시작일", example = "2026-10-01")
            LocalDate startDate,
            @JsonFormat(pattern = "yyyy-MM-dd")
            @Schema(description = "실제 조회 종료일", example = "2026-10-31")
            LocalDate endDate
    ) {}

    @Schema(description = "캘린더 행사 정보")
    public record CalendarContentInfo(
            @Schema(description = "상세 조회에 사용하는 문화콘텐츠 ID")
            Long contentId,
            @Schema(description = "행사명")
            String title,
            @Schema(description = "카테고리", example = "FESTIVAL")
            ContentCategory category,
            @Schema(description = "대표 이미지 URL")
            String thumbnailImageUrl,
            @Schema(description = "지역명")
            String regionName,
            @Schema(description = "해시태그 이름 목록")
            List<String> hashtags,
            @Schema(description = "좋아요 수")
            Long likeCount,
            @Schema(description = "현재 사용자의 좋아요 여부. 비로그인 시 false")
            Boolean isLiked,
            @JsonFormat(pattern = "yyyy.MM.dd")
            @Schema(description = "실제 행사 시작일", example = "2026.09.30")
            LocalDate startDate,
            @JsonFormat(pattern = "yyyy.MM.dd")
            @Schema(description = "실제 행사 종료일", example = "2026.10.02")
            LocalDate endDate
    ) {}

    @Schema(description = "문화콘텐츠 목록 정보")
    public record ContentInfo(

            @Schema(description = "문화콘텐츠 ID")
            Long contentId,

            @Schema(description = "장소 ID")
            Long placeId,

            @Schema(description = "장소 위도")
            BigDecimal latitude,

            @Schema(description = "장소 경도")
            BigDecimal longitude,

            @Schema(description = "제목")
            String title,

            @Schema(description = "카테고리", example = "FESTIVAL")
            ContentCategory category,

            @Schema(description = "썸네일 이미지")
            String thumbnailImageUrl,

            @Schema(description = "지역명")
            String regionName,

            @Schema(description = "해시태그")
            List<String> hashtags,

            @Schema(description = "좋아요 수")
            Long likeCount,

            @Schema(description = "현재 사용자의 좋아요 여부", example = "false")
            Boolean isLiked,

            @JsonFormat(pattern = "yyyy.MM")
            @Schema(description = "시작일", example = "2026.07")
            LocalDate startDate,
      
            @JsonFormat(pattern = "yyyy.MM")
            @Schema(description = "종료일", example = "2026.07")
            LocalDate endDate,

            @Schema(description = "게시 상태", example = "PUBLISHED")
            ContentPublicationStatus publicationStatus
      
           
    ) {}


    @Schema(description = "문화콘텐츠 상세 조회")
    public record ContentDetailRes(

            @Schema(description = "문화콘텐츠 ID")
            Long contentId,

            @Schema(description = "제목")
            String title,

            @Schema(description = "설명")
            String description,

            @Schema(description = "카테고리", example = "FESTIVAL")
            ContentCategory category,

            @Schema(description = "썸네일")
            String thumbnailImage,

            @Schema(description = "해시태그")
            List<String> hashtags,

            @Schema(description = "해시태그 ID 목록")
            List<Long> hashtagIds,

            @JsonFormat(pattern = "yyyy.MM.dd")
            @Schema(description = "시작일", example = "2026.07.04")
            LocalDate startDate,

            @JsonFormat(pattern = "yyyy.MM.dd")
            @Schema(description = "종료일", example = "2026.07.05")
            LocalDate endDate,

            @Schema(description = "좋아요 여부")
            Boolean liked,

            @Schema(description = "전화번호")
            String phone,

            @Schema(description = "공식 홈페이지와 SNS 등 외부 링크 목록")
            List<OfficialLink> officialLinks,

            @Schema(description = "장소 정보")
            PlaceInfo place,

            @Schema(description = "코스 정보")
            List<CourseInfo> courses
    ) {}

    public record OfficialLink(
            @Schema(description = "링크 유형", example = "INSTAGRAM")
            ContentLinkType type,

            @Schema(description = "화면 표시명", example = "공식 인스타그램")
            String label,

            @Schema(description = "링크 URL")
            String url
    ) {}

    @Schema(description = "장소 정보")
    public record PlaceInfo(

            @Schema(description = "장소 ID")
            Long placeId,

            @Schema(description = "외부 장소 식별 ID")
            String externalPlaceId,

            @Schema(description = "장소 데이터 출처")
            PlaceSource source,

            @Schema(description = "장소명")
            String name,

            @Schema(description = "도로명 주소")
            String roadAddress,

            @Schema(description = "지번 주소")
            String lotAddress,

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

            @JsonFormat(pattern = "yyyy.MM.dd")
            @Schema(description = "행사 시작일", example = "2026.07.04")
            LocalDate startDate,

            @JsonFormat(pattern = "yyyy.MM.dd")
            @Schema(description = "행사 종료일", example = "2026.07.05")
            LocalDate endDate
    ) {}

}

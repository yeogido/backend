package com.yeogido.backend.domain.course.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeogido.backend.domain.content.enums.ContentStatus;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportMode;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.user.enums.AgeGroup;
import com.yeogido.backend.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

public class CourseResDTO {

    @Schema(name = "CourseIdResponse", description = "추천 코스 ID 응답")
    public record CourseIdRes(

            @Schema(description = "코스 ID", example = "15")
            Long courseId
    ) { }

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

    @Schema(name = "CourseRecommendedPreviewResponse", description = "메인 배너 추천 코스 정보")
    public record CourseRecommendedPreview(

            @Schema(description = "코스 ID", example = "1")
            Long courseId,

            @Schema(description = "코스 제목", example = "강릉 혼자 여행 코스")
            String title,

            @Schema(description = "코스 설명", example = "바다를 따라 걷고, 감성 가득한 카페와 로컬 맛집을 즐기는 강릉 여행 코스입니다.")
            String description,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/course1.jpg")
            String thumbnailUrl,

            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType
    ) { }

    @Schema(name = "CourseLocalPopularPreviewResponse", description = "인기 로컬 코스 미리보기 정보")
    public record CourseLocalPopularPreview(

            @Schema(description = "코스 ID", example = "1")
            Long courseId,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/course1.jpg")
            String thumbnailUrl,

            @Schema(description = "코스 제목", example = "강릉 혼자 여행 코스")
            String title,

            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @Schema(description = "동행 유형", example = "SOLO")
            CompanionType companionType,

            @Schema(description = "작성자 정보")
            LocalPopularAuthor author,

            @Schema(description = "생성일시", example = "2026-07-26T15:30:00")
            LocalDateTime createdAt,

            @Schema(description = "태그", example = "[\"여름\", \"자연\", \"바다\"]")
            List<String> tags,

            @Schema(description = "현재 사용자의 코스 좋아요 여부", example = "true")
            Boolean isLiked
    ) { }

    @Schema(name = "CourseLocalPopularAuthorResponse", description = "인기 로컬 코스 작성자 정보")
    public record LocalPopularAuthor(

            @Schema(description = "작성자 사용자 ID", example = "10")
            Long userId,

            @Schema(description = "작성자 닉네임", nullable = true, example = "홍길동")
            String nickname,

            @Schema(description = "작성자 프로필 이미지 URL", nullable = true, example = "https://example.com/profile.jpg")
            String profileImageUrl
    ) { }

    @Schema(name = "CourseDetailResponse", description = "추천 코스 상세 조회 응답")
    public record CourseDetail(

            @Schema(description = "코스 ID", example = "1")
            Long courseId,

            @Schema(description = "코스 타입", example = "OFFICIAL")
            CourseType courseType,

            @Schema(description = "코스 제목", example = "강릉 혼자 여행 코스")
            String title,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/course1.jpg")
            String thumbnailUrl,

            @Schema(description = "코스 설명", example = "바다를 따라 걷고, 감성 가득한 카페와 로컬 맛집을 즐기는 강릉 여행 코스입니다.")
            String description,

            @Schema(description = "태그", example = "[\"여름\", \"자연\", \"바다\", \"카페\"]")
            List<String> tags,

            @Schema(description = "여행 기간", example = "TWO_NIGHT")
            DurationType durationType,

            @Schema(description = "이동 수단", example = "WALK")
            TransportType transportType,

            @Schema(description = "추천 시작 월", example = "4")
            Integer startMonth,

            @Schema(description = "추천 종료 월", example = "10")
            Integer endMonth,

            @Schema(description = "동행 유형", example = "SOLO")
            CompanionType companionType,

            @Schema(description = "현재 사용자의 코스 좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "코스 구성 항목")
            List<CourseItem> courseItems,

            @Schema(description = "작성자 정보")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Author author
    ) { }

    @Schema(name = "CourseSummaryResponse", description = "추천 코스 요약 조회 응답")
    public record CourseSummary(

            @Schema(description = "코스 ID", example = "1")
            Long courseId,

            @Schema(description = "코스 제목", example = "강릉 혼자 여행 코스")
            String title,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/course1.jpg")
            String thumbnailUrl,

            @Schema(description = "여행 기간", example = "TWO_NIGHT")
            DurationType durationType,

            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType,

            @Schema(description = "동행 유형", example = "FRIEND")
            CompanionType companionType
    ) { }

    @Schema(
            name = "CourseItemResponse",
            description = "코스 구성 항목",
            oneOf = {PlaceCourseItem.class, ContentCourseItem.class}
    )
    public sealed interface CourseItem permits PlaceCourseItem, ContentCourseItem {

        Integer order();

        CourseItemType type();

        Boolean isLiked();

        PlaceSource source();

        String externalPlaceId();

        String categoryGroupCode();

        String name();

        String roadAddress();

        String lotAddress();

        BigDecimal latitude();

        BigDecimal longitude();

        String imageUrl();

        List<CourseItemTime> timesFromPrevious();
    }

    @Schema(name = "PlaceOperatingDayResponse", description = "장소 영업시간 응답")
    public record OperatingDay(

            @Schema(description = "영업 요일", example = "MONDAY")
            DayOfWeek dayOfWeek,

            @Schema(description = "영업 시작 시간", example = "09:00")
            LocalTime openTime,

            @Schema(description = "영업 종료 시간", example = "18:00")
            LocalTime closeTime
    ) { }

    @Schema(name = "CourseItemTimeResponse", description = "이전 코스 항목에서 현재 항목까지의 이동 소요시간 응답")
    public record CourseItemTime(

            @Schema(description = "이동 방식", example = "WALK")
            TransportMode transportMode,

            @Schema(description = "이동 소요시간(분)", example = "15")
            Integer durationMinutes
    ) { }

    @Schema(name = "PlaceCourseItemResponse", description = "장소 코스 구성 항목")
    public record PlaceCourseItem(

            @Schema(description = "코스 내 순서", example = "1")
            Integer order,

            @Schema(description = "코스 항목 타입", example = "PLACE")
            CourseItemType type,

            @Schema(description = "장소 ID", example = "10")
            Long placeId,

            @Schema(description = "현재 사용자의 장소 좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "장소 정보 출처", example = "KAKAO")
            PlaceSource source,

            @Schema(description = "외부 장소 ID", example = "123456789")
            String externalPlaceId,

            @Schema(description = "카테고리 그룹 코드", nullable = true, example = "AT4")
            String categoryGroupCode,

            @Schema(description = "장소명", example = "주문진 해변")
            String name,

            @Schema(description = "도로명 주소", example = "강원특별자치도 강릉시 해안로 1609")
            String roadAddress,

            @Schema(description = "지번 주소", example = "강원특별자치도 강릉시 주문진읍 향호리")
            String lotAddress,

            @Schema(description = "위도", example = "37.9111111")
            BigDecimal latitude,

            @Schema(description = "경도", example = "128.8211111")
            BigDecimal longitude,

            @Schema(description = "장소 이미지 키. 추천 코스 수정 시 기존 이미지를 유지하기 위해 사용합니다.", nullable = true, example = "courses/place/jumunjin.jpg")
            String imageKey,

            @Schema(description = "장소 이미지 URL. 화면 표시용입니다.", nullable = true, example = "https://example.com/place/jumunjin.jpg")
            String imageUrl,

            @Schema(description = "장소 영업시간 목록. 없으면 빈 배열입니다.")
            List<OperatingDay> operatingDays,

            @Schema(description = "이전 코스 항목에서 현재 항목까지의 이동 소요시간 목록. 없으면 빈 배열입니다.")
            List<CourseItemTime> timesFromPrevious
    ) implements CourseItem { }

    @Schema(name = "ContentCourseItemResponse", description = "문화 콘텐츠 코스 구성 항목")
    public record ContentCourseItem(

            @Schema(description = "코스 내 순서", example = "2")
            Integer order,

            @Schema(description = "코스 항목 타입", example = "CONTENT")
            CourseItemType type,

            @Schema(description = "콘텐츠 ID", example = "20")
            Long contentId,

            @Schema(description = "현재 사용자의 콘텐츠 좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "콘텐츠 진행 상태", example = "ONGOING")
            ContentStatus contentStatus,

            @Schema(description = "장소 정보 출처", example = "KAKAO")
            PlaceSource source,

            @Schema(description = "외부 장소 ID", example = "123456789")
            String externalPlaceId,

            @Schema(description = "카테고리 그룹 코드", nullable = true, example = "AT4")
            String categoryGroupCode,

            @Schema(description = "콘텐츠명", example = "강릉 커피 축제")
            String name,

            @Schema(description = "도로명 주소", example = "강원특별자치도 강릉시 해안로 1609")
            String roadAddress,

            @Schema(description = "지번 주소", example = "강원특별자치도 강릉시 주문진읍 향호리")
            String lotAddress,

            @Schema(description = "위도", example = "37.9111111")
            BigDecimal latitude,

            @Schema(description = "경도", example = "128.8211111")
            BigDecimal longitude,

            @Schema(description = "콘텐츠 이미지 URL. CONTENT 타입은 imageKey를 응답하지 않습니다.", nullable = true, example = "https://example.com/content/coffee-festival.jpg")
            String imageUrl,

            @Schema(description = "이전 코스 항목에서 현재 항목까지의 이동 소요시간 목록. 없으면 빈 배열입니다.")
            List<CourseItemTime> timesFromPrevious
    ) implements CourseItem { }

    @Schema(name = "CourseAuthorResponse", description = "코스 작성자 정보")
    public record Author(

            @Schema(description = "작성자 닉네임", nullable = true, example = "홍길동")
            String nickname,

            @Schema(description = "작성자 프로필 이미지 URL", nullable = true, example = "https://example.com/profile.jpg")
            String profileImageUrl
    ) { }

    @Schema(name = "CourseReviewCreateResponse", description = "추천 코스 리뷰 작성 응답")
    public record ReviewCreateRes(

            @Schema(description = "리뷰 ID", example = "1")
            Long reviewId
    ) { }

    @Schema(name = "CourseReviewPreviewResponse", description = "추천 코스 리뷰 목록 정보")
    public record ReviewPreview(

            @Schema(description = "리뷰 ID", example = "1")
            Long reviewId,

            @Schema(description = "작성자 정보")
            ReviewAuthor author,

            @Schema(description = "별점", example = "5")
            Integer rating,

            @Schema(description = "리뷰 내용", example = "동선이 편하고 장소 구성이 좋았어요.")
            String content,

            @Schema(description = "리뷰 이미지 목록")
            List<ReviewImage> images,

            @Schema(description = "생성일", example = "2026-07-26")
            LocalDate createdAt
    ) { }

    @Schema(name = "CourseReviewAuthorResponse", description = "추천 코스 리뷰 작성자 정보")
    public record ReviewAuthor(

            @Schema(description = "닉네임", example = "민지")
            String nickname,

            @Schema(description = "연령대", example = "TWENTIES")
            AgeGroup ageGroup,

            @Schema(description = "성별", example = "FEMALE")
            Gender gender,

            @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
            String profileImageUrl
    ) { }

    @Schema(name = "CourseReviewImageResponse", description = "추천 코스 리뷰 이미지 정보")
    public record ReviewImage(

            @Schema(description = "리뷰 이미지 key. 리뷰 수정 시 기존 이미지를 유지하기 위해 사용합니다.", example = "reviews/abc.jpg")
            String imageKey,

            @Schema(description = "리뷰 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/reviews/abc.jpg")
            String imageUrl,

            @Schema(description = "리뷰 이미지 순서", example = "1")
            Integer imageOrder
    ) { }

    @Schema(name = "CourseLikeResponse", description = "추천 코스 좋아요 등록 응답")
    public record CourseLikeRes(

            @Schema(description = "좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "좋아요 수", example = "121")
            Long likeCount
    ) { }
}

package com.yeogido.backend.domain.course.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
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

    @Schema(name = "CourseItemResponse", description = "코스 구성 항목")
    public record CourseItem(

            @Schema(description = "코스 내 순서", example = "1")
            Integer order,

            @Schema(description = "코스 항목 타입", example = "PLACE")
            CourseItemType type,

            @Schema(description = "장소 ID", example = "11")
            Long placeId,

            @Schema(description = "장소 정보 출처", example = "KAKAO")
            PlaceSource source,

            @Schema(description = "외부 장소 ID", example = "123456789")
            String externalPlaceId,

            @Schema(description = "장소명", example = "주문진 해변")
            String name,

            @Schema(description = "도로명 주소", example = "강원특별자치도 강릉시 해안로 1609")
            String roadAddress,

            @Schema(description = "지번 주소", example = "강원특별자치도 강릉시 주문진읍 향호리")
            String lotAddress,

            @Schema(description = "위도", example = "37.9111111")
            BigDecimal latitude,

            @Schema(description = "경도", example = "128.8211111")
            BigDecimal longitude
    ) { }

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

    @Schema(name = "CourseLikeResponse", description = "추천 코스 좋아요 등록 응답")
    public record CourseLikeRes(

            @Schema(description = "좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "좋아요 수", example = "121")
            Long likeCount
    ) { }
}

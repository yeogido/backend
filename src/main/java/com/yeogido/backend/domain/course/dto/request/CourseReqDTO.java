package com.yeogido.backend.domain.course.dto.request;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class CourseReqDTO {

    @Schema(name = "CourseCreateRequest", description = "추천 코스 등록 요청")
    public record CourseCreateReq(

            @NotBlank(message = "코스 제목은 필수입니다")
            @Schema(description = "코스 제목", example = "부산 야경 여행")
            String title,

            @Schema(description = "코스 설명", example = "부산의 야경과 축제를 함께 즐길 수 있는 코스입니다.")
            String description,

            @NotNull(message = "여행 기간은 필수입니다")
            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @NotNull(message = "이동 수단은 필수입니다")
            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType,

            @NotNull(message = "동행 유형은 필수입니다")
            @Schema(description = "동행 유형", example = "FRIEND")
            CompanionType companionType,

            @Schema(description = "대표 이미지 S3 key", example = "courses/thumbnail/abcd1234.jpg")
            String thumbnailKey,

            @Schema(description = "해시태그 ID 목록", example = "[1, 3, 5]")
            List<Long> hashtagIds,

            @Valid
            @NotEmpty
            @Schema(description = "코스 구성 항목")
            List<CourseItemCreateReq> courseItems
    ) { }

    @Schema(name = "CourseItemCreateRequest", description = "추천 코스 구성 항목 등록 요청")
    public record CourseItemCreateReq(

            @NotNull(message = "코스 항목 순서는 필수입니다")
            @Schema(description = "코스 내 순서", example = "1")
            Integer order,

            @NotNull(message = "코스 항목 타입은 필수입니다")
            @Schema(description = "코스 항목 타입. PLACE인 경우 placeId와 imageKey를 사용하고, CONTENT인 경우 contentId를 사용합니다.", example = "PLACE")
            CourseItemType type,

            @Schema(description = "장소 ID. type=PLACE인 경우 사용합니다.", example = "1")
            Long placeId,

            @Schema(description = "콘텐츠 ID. type=CONTENT인 경우 사용합니다.", example = "10")
            Long contentId,

            @Schema(description = "장소 이미지 S3 key. type=PLACE인 경우에만 사용합니다.", nullable = true, example = "courses/place/efgh1234.jpg")
            String imageKey
    ) { }

    @Schema(name = "CourseListRequest", description = "추천 코스 목록 조회 요청")
    public record CourseListReq(

            @NotNull(message = "코스 타입은 필수입니다")
            @Schema(description = "코스 타입", example = "OFFICIAL")
            CourseType courseType,

            @Schema(description = "검색어", example = "강릉")
            String keyword,

            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType,

            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @Schema(description = "동행 유형", example = "SOLO")
            CompanionType companionType,

            @Schema(description = "정렬 기준", example = "RECOMMEND")
            CourseSortType sort,

            @Schema(description = "커서 ID", example = "1")
            Long cursor,

            @Schema(description = "조회 개수", example = "20", defaultValue = "20")
            Integer size
    ) {
        public CourseListReq {
            if (size == null) {
                size = 20;
            }
        }
    }

    @Schema(name = "CoursePopularRequest", description = "인기 추천 코스 미리보기 조회 요청")
    public record CoursePopularReq(

            @NotNull(message = "코스 타입은 필수입니다")
            @Schema(description = "코스 타입", example = "OFFICIAL")
            CourseType courseType
    ) { }

    @Schema(name = "CourseReviewCreateRequest", description = "추천 코스 리뷰 작성 요청")
    public record ReviewCreateReq(

            @NotNull(message = "별점은 필수입니다")
            @Schema(description = "별점", example = "5.0")
            BigDecimal rating,

            @Schema(description = "리뷰 내용", example = "지도 동선이 편하고 여행하기 좋았습니다.")
            String content
    ) { }
}

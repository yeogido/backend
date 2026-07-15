package com.yeogido.backend.domain.course.dto.request;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class CourseReqDTO {

    @Schema(name = "CourseCreateRequest", description = "추천 코스 등록 요청")
    public record CourseCreateReq(

            @NotBlank(message = "코스 제목은 필수입니다")
            @Schema(description = "코스 제목", example = "부산 야경 여행")
            String title,

            @NotNull(message = "지역은 필수입니다")
            @Schema(description = "코스 지역 ID", example = "26")
            Long regionId,

            @Schema(description = "코스 설명", example = "부산의 야경과 축제를 함께 즐길 수 있는 코스입니다.")
            @NotBlank(message = "코스 설명은 필수입니다")
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
            @NotBlank(message = "대표 이미지는 필수입니다")
            String thumbnailKey,

            @Schema(description = "해시태그 ID 목록", example = "[1, 3, 5]")
            @NotNull(message = "해시태그를 선택해주세요.")
            @Size(
                    min = 1,
                    max = 5,
                    message = "해시태그는 1개 이상 5개 이하로 선택할 수 있습니다."
            )
            List<@NotNull(message = "유효하지 않은 해시태그입니다.") Long> hashtagIds,

            @Valid
            @NotEmpty(message = "코스 구성 항목은 최소 1개 이상 필요합니다.")
            @Schema(description = "코스 구성 항목")
            List<@NotNull(message = "코스 구성 항목이 올바르지 않습니다.") CourseItemCreateReq> courseItems
    ) { }

    @Schema(name = "CourseUpdateRequest", description = "추천 코스 수정 요청")
    public record CourseUpdateReq(

            @Schema(description = "코스 제목", example = "부산 야경 여행")
            String title,

            @Schema(description = "코스 설명", example = "부산의 야경과 축제를 함께 즐길 수 있는 코스입니다.")
            String description,

            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType,

            @Schema(description = "동행 유형", example = "FRIEND")
            CompanionType companionType,

            @Schema(description = "대표 이미지 S3 key", example = "courses/thumbnail/abcd1234.jpg")
            String thumbnailKey,

            @Schema(description = "해시태그 ID 목록", example = "[1, 3, 5]")
            @Size(
                    max = 5,
                    message = "해시태그는 5개 이하로 선택할 수 있습니다."
            )
            List<@NotNull(message = "유효하지 않은 해시태그입니다.") Long> hashtagIds,

            @Valid
            @Schema(description = "코스 구성 항목")
            List<@NotNull(message = "코스 구성 항목이 올바르지 않습니다.") CourseItemCreateReq> courseItems
    ) { }

    @Schema(name = "CourseItemCreateRequest", description = "추천 코스 구성 항목 등록 요청")
    public record CourseItemCreateReq(

            @NotNull(message = "코스 항목 순서는 필수입니다")
            @Schema(description = "코스 내 순서", example = "1")
            Integer order,

            @NotNull(message = "코스 항목 타입은 필수입니다")
            @Schema(description = "PLACE인 경우 externalPlaceId, categoryGroupCode, name, roadAddress(또는 lotAddress), latitude, longitude, imageKey를 사용하고, CONTENT인 경우 contentId를 사용합니다.", example = "PLACE")
            CourseItemType type,

            @Schema(description = "콘텐츠 ID. type=CONTENT인 경우 사용합니다.", example = "10")
            Long contentId,

            @Schema(description = "외부 장소 ID. type=PLACE인 경우 필수입니다.", example = "123456")
            String externalPlaceId,

            @Schema(description = "카테고리 그룹 코드. type=PLACE인 경우 필수입니다.", example = "AT4")
            String categoryGroupCode,

            @Schema(description = "장소명. type=PLACE인 경우 필수입니다.", example = "광안리")
            String name,

            @Schema(description = "도로명 주소. type=PLACE인 경우 필수입니다.", example = "부산 수영구 광안해변로 219")
            String roadAddress,

            @Schema(description = "지번 주소. type=PLACE인 경우 선택입니다.", example = "부산 수영구 광안동 192-20")
            String lotAddress,

            @Schema(description = "위도. type=PLACE인 경우 필수입니다.", example = "35.1531698")
            BigDecimal latitude,

            @Schema(description = "경도. type=PLACE인 경우 필수입니다.", example = "129.118666")
            BigDecimal longitude,

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
            @DecimalMin(value = "0.0", message = "별점은 0 이상이어야 합니다")
            @DecimalMax(value = "5.0", message = "별점은 5 이하여야 합니다")
            @Schema(description = "별점", example = "5")
            BigDecimal rating,

            @Schema(description = "리뷰 내용", example = "지도 동선이 편하고 여행하기 좋았습니다.")
            String content
    ) { }
}

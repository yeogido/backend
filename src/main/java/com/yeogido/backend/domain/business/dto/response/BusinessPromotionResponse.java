package com.yeogido.backend.domain.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class BusinessPromotionResponse {
    @Builder
    @Schema(name = "BusinessPromotionRegisterResponse", description = "소상공인 홍보 등록 응답")
    public record Register(

            @Schema(description = "소상공인 홍보ID", example = "1")
            Long promotionId
    ){ }

    @Builder
    @Schema(name = "BusinessPromotionListResponse", description = "소상공인 홍보 목록 조회 응답")
    public record ListResult(

            @Schema(description = "홍보글 목록")
            List<Summary> promotions,

            @Schema(description = "다음 페이지 커서", example = "1")
            Long nextCursor,

            @Schema(description = "다음 페이지 존재 여부", example = "true")
            Boolean hasNext
    ){ }

    @Builder
    @Schema(name = "BusinessPromotionSummaryResponse", description = "홍보글 목록 정보")
    public record Summary(

            @Schema(description = "소상공인 홍보 ID", example = "1")
            Long promotionId,

            @Schema(description = "장소 ID", example = "10")
            Long placeId,

            @Schema(description = "장소명", example = "웨이브온 커피")
            String placeName,

            @Schema(description = "카테고리 그룹명", example = "카페")
            String categoryGroupName,

            @Schema(description = "도로명 주소", example = "부산 기장군 장안읍 해맞이로 286")
            String roadAddress,

            @Schema(description = "지역 ID", example = "26")
            Long regionId,

            @Schema(description = "지역명", example = "부산광역시")
            String regionName,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
            String thumbnailImageUrl,

            @Schema(description = "짧은 소개글", example = "바다 뷰 완전 잘 보이는 카페!")
            String shortDescription,

            @Schema(description = "연결된 장소의 좋아요 수", example = "24")
            Integer likeCount,

            @Schema(description = "현재 사용자의 장소 좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "생성일시", example = "2026-07-05T10:30:00")
            LocalDateTime createdAt
    ){ }

    @Builder
    @Schema(name = "BusinessPromotionDetailResponse", description = "소상공인 홍보글 상세 조회 응답")
    public record Detail(
            @Schema(description = "소상공인 홍보 ID", example = "1")
            Long promotionId,

            @Schema(description = "장소 정보")
            PlaceInfo place,

            @Schema(description = "짧은 소개글", example = "바다 뷰 완전 잘 보이는 카페!")
            String shortDescription,

            @Schema(description = "사장님의 한마디", example = "부산 바다를 담은 공간, 웨이브온 커피에 오신 걸 환영합니다!")
            String ownerComment,

            @Schema(description = "요일별 영업시간")
            List<BusinessHourInfo> businessHours,

            @Schema(description = "SNS 계정", example = "https://instagram.com/waveoncoffee")
            String snsAccount,

            @Schema(description = "전화번호", example = "051-727-1660")
            String phoneNumber,

            @Schema(description = "해시태그", example = "[\"오션뷰\", \"부산카페\", \"디저트\"]")
            List<String> hashtags,

            @Schema(description = "홍보 이미지")
            List<ImageInfo> images,

            @Schema(description = "연결된 장소의 좋아요 수", example = "24")
            Integer likeCount,

            @Schema(description = "현재 사용자의 장소 좋아요 여부", example = "true")
            Boolean isLiked,

            @Schema(description = "생성일시", example = "2026-07-05T10:30:00")
            LocalDateTime createdAt,

            @Schema(description = "수정일시", example = "2026-07-05T12:00:00")
            LocalDateTime updatedAt
    ){ }

    @Builder
    @Schema(description = "장소 정보")
    public record PlaceInfo(

            @Schema(description = "장소 ID", example = "10")
            Long placeId,

            @Schema(description = "장소명", example = "웨이브온 커피")
            String name,

            @Schema(description = "카테고리", example = "음식점 > 카페 > 커피전문점")
            String category,

            @Schema(description = "카테고리 그룹명", example = "카페")
            String categoryGroupName,

            @Schema(description = "도로명 주소", example = "부산 기장군 장안읍 해맞이로 286")
            String roadAddress,

            @Schema(description = "지번 주소", example = "부산 기장군 장안읍 월내리 553")
            String lotAddress,

            @Schema(description = "위도", example = "35.3214567")
            BigDecimal latitude,

            @Schema(description = "경도", example = "129.2741234")
            BigDecimal longitude,

            @Schema(description = "지역 ID", example = "26")
            Long regionId,

            @Schema(description = "지역명", example = "부산광역시")
            String regionName
    ) { }

    @Builder
    @Schema(description = "요일별 영업시간")
    public record BusinessHourInfo(

            @Schema(description = "요일", example = "MONDAY")
            String dayOfWeek,

            @Schema(description = "오픈 시간", example = "10:00")
            LocalTime openTime,

            @Schema(description = "마감 시간", example = "22:00")
            LocalTime closeTime
    ) { }

    @Builder
    @Schema(description = "홍보 이미지")
    public record ImageInfo(

            @Schema(description = "이미지 URL", example = "https://example.com/image.jpg")
            String imageUrl,

            @Schema(description = "이미지 정렬 순서", example = "1")
            Integer sortOrder
    ) { }

    @Builder
    @Schema(name = "MyBusinessPromotionListResponse", description = "내가 등록한 홍보글 조회 응답")
    public record MyListResult(

            @Schema(description = "내가 등록한 홍보글 목록")
            List<MySummary> promotions,

            @Schema(description = "다음 페이지 커서", example = "1")
            Long nextCursor,

            @Schema(description = "다음 페이지 존재 여부", example = "true")
            Boolean hasNext
    ) { }

    @Builder
    @Schema(name = "MyBusinessPromotionSummaryResponse", description = "내가 등록한 홍보글 목록 정보")
    public record MySummary(

            @Schema(description = "소상공인 홍보 ID", example = "1")
            Long promotionId,

            @Schema(description = "장소 ID", example = "10")
            Long placeId,

            @Schema(description = "장소명", example = "웨이브온 커피")
            String placeName,

            @Schema(description = "카테고리 그룹명", example = "카페")
            String categoryGroupName,

            @Schema(description = "도로명 주소", example = "부산 기장군 장안읍 해맞이로 286")
            String roadAddress,

            @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
            String thumbnailImageUrl,

            @Schema(description = "짧은 소개글", example = "바다 뷰 완전 잘 보이는 카페!")
            String shortDescription,

            @Schema(description = "홍보글 상태", example = "ACTIVE")
            String status,

            @Schema(description = "연결된 장소의 좋아요 수", example = "24")
            Integer likeCount,

            @Schema(description = "생성일시", example = "2026-07-05T10:30:00")
            LocalDateTime createdAt,

            @Schema(description = "수정일시", example = "2026-07-05T12:00:00")
            LocalDateTime updatedAt
    ) { }
}

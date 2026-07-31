package com.yeogido.backend.domain.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TravelRecordResDTO {

    @Schema(name = "TravelRecordYearListResponse", description = "내 여행 기록 연도 목록 조회 응답")
    public record YearListResponse(
            @Schema(description = "여행 기록이 존재하는 연도 목록", example = "[2026, 2025, 2024]")
            List<Integer> years
    ) {
    }

    @Schema(name = "TravelRecordSummary", description = "여행 기록 목록 아이템")
    public record TravelRecordSummary(
            @Schema(description = "여행 기록 ID", example = "20")
            Long travelRecordId,

            @Schema(description = "여행 기록 제목", example = "부산 감성 바다 여행")
            String title,

            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "여행 시작일", example = "2026-05-22")
            LocalDate startDate,

            @Schema(description = "여행 종료일", example = "2026-05-24")
            LocalDate endDate,

            @Schema(description = "대표 이미지 key", example = "travel-records/20/image-1.jpg")
            String coverImageKey,

            @Schema(description = "대표 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/travel-records/20/image-1.jpg")
            String coverImageUrl,

            @Schema(description = "폴더 색상/테마", example = "BASIC")
            String folderTheme,

            @Schema(description = "생성 일시", example = "2026-05-22T14:30:00")
            LocalDateTime createdAt
    ) {
    }

    @Schema(name = "TravelRecordDetailResponse", description = "여행 기록 상세 조회 응답")
    public record DetailResponse(
            @Schema(description = "여행 기록 ID", example = "1")
            Long travelRecordId,

            @Schema(description = "여행 기록 제목", example = "부산 감성 바다 여행")
            String title,

            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "여행 시작일", example = "2026-05-22")
            LocalDate startDate,

            @Schema(description = "여행 종료일", example = "2026-05-24")
            LocalDate endDate,

            @Schema(description = "대표 이미지 key", example = "travel-records/1/image-1.jpg")
            String coverImageKey,

            @Schema(description = "대표 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/travel-records/1/image-1.jpg")
            String coverImageUrl,

            @Schema(description = "폴더 색상/테마", example = "BASIC")
            String folderTheme,

            @Schema(description = "여행 기록 사진 목록")
            List<ImageResponse> images,

            @Schema(description = "여행 기록 스티커 목록")
            List<StickerResponse> stickers,

            @Schema(description = "생성 일시", example = "2026-05-22T14:30:00")
            LocalDateTime createdAt,

            @Schema(description = "수정 일시", nullable = true)
            LocalDateTime updatedAt
    ) {
    }

    @Schema(name = "TravelRecordImageResponse", description = "여행 기록 사진 응답")
    public record ImageResponse(
            @Schema(description = "사진 ID", example = "1")
            Long imageId,

            @Schema(description = "사진 이미지 key", example = "travel-records/1/image-1.jpg")
            String imageKey,

            @Schema(description = "사진 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/travel-records/1/image-1.jpg")
            String imageUrl,

            @Schema(description = "사진 순서", example = "1")
            Integer imageOrder
    ) {
    }

    @Schema(name = "TravelRecordStickerResponse", description = "여행 기록 스티커 응답")
    public record StickerResponse(
            @Schema(description = "기록 스티커 ID", example = "1")
            Long recordStickerId,

            @Schema(description = "스티커 ID", example = "1")
            Long stickerId,

            @Schema(description = "스티커 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/stickers/heart.png")
            String imageUrl,

            @Schema(description = "스티커 X 좌표", example = "120.5")
            Double positionX,

            @Schema(description = "스티커 Y 좌표", example = "80.0")
            Double positionY,

            @Schema(description = "스티커 회전값", example = "15.0")
            Double rotation,

            @Schema(description = "스티커 크기 비율", example = "1.2")
            Double scale,

            @Schema(description = "스티커 겹침 순서", example = "1")
            Integer zIndex
    ) {
    }

    @Schema(name = "TravelRecordCreateResponse", description = "여행 기록 등록 응답")
    public record CreateResponse(
            @Schema(description = "여행 기록 ID", example = "1")
            Long travelRecordId,

            @Schema(description = "여행 기록 제목", example = "부산 감성 바다 여행")
            String title,

            @Schema(description = "대표 이미지 key", example = "travel-records/1/image-1.jpg")
            String coverImageKey,

            @Schema(description = "생성 일시", example = "2026-05-22T14:30:00")
            LocalDateTime createdAt
    ) {
    }

    @Schema(name = "TravelRecordUpdateResponse", description = "여행 기록 수정 응답")
    public record UpdateResponse(
            @Schema(description = "수정된 여행 기록 ID", example = "1")
            Long travelRecordId
    ) {
    }
}

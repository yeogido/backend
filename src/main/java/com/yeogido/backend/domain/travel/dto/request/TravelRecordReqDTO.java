package com.yeogido.backend.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public class TravelRecordReqDTO {

    @Schema(name = "TravelRecordListRequest", description = "내 여행 기록 목록 조회 요청")
    public record ListRequest(
            @Schema(description = "마지막으로 조회한 여행 기록 ID", example = "20")
            @Positive(message = "cursor는 양수여야 합니다.")
            Long cursor,

            @Schema(description = "조회할 여행 기록 개수", example = "10", defaultValue = "10")
            @Positive(message = "size는 양수여야 합니다.")
            Integer size,

            @Schema(description = "조회할 여행 연도", example = "2026", defaultValue = "현재 연도")
            @Positive(message = "year는 양수여야 합니다.")
            Integer year
    ) {
    }

    @Schema(name = "TravelRecordCreateRequest", description = "여행 기록 등록 요청")
    public record CreateRequest(
            @Schema(description = "여행 기록 제목", example = "부산 감성 바다 여행")
            @NotBlank(message = "여행 기록 제목은 필수입니다.")
            String title,

            @Schema(description = "지역 ID", example = "1")
            @NotNull(message = "지역 ID는 필수입니다.")
            @Positive(message = "지역 ID는 양수여야 합니다.")
            Long regionId,

            @Schema(description = "여행 시작일", example = "2026-05-22")
            @NotNull(message = "여행 시작일은 필수입니다.")
            LocalDate startDate,

            @Schema(description = "여행 종료일", example = "2026-05-24")
            @NotNull(message = "여행 종료일은 필수입니다.")
            LocalDate endDate,

            @Schema(description = "폴더 색상/테마", example = "BASIC")
            String folderTheme,

            @Schema(description = "여행 기록 사진 목록")
            @Valid
            @NotEmpty(message = "여행 기록 사진은 최소 1장 이상 등록해야 합니다.")
            @Size(min = 1, max = 5, message = "여행 기록 사진은 최소 1장 이상, 최대 5장까지 등록할 수 있습니다.")
            List<ImageRequest> images,

            @Schema(description = "여행 기록 폴더 위에 배치할 스티커 목록")
            @Valid
            List<StickerRequest> stickers
    ) {
    }

    @Schema(name = "TravelRecordUpdateRequest", description = "여행 기록 수정 요청")
    public record UpdateRequest(
            @Schema(description = "여행 기록 제목", example = "부산 바다 여행 기록 수정")
            @NotBlank(message = "여행 기록 제목은 필수입니다.")
            String title,

            @Schema(description = "지역 ID", example = "1")
            @NotNull(message = "지역 ID는 필수입니다.")
            @Positive(message = "지역 ID는 양수여야 합니다.")
            Long regionId,

            @Schema(description = "여행 시작일", example = "2026-05-22")
            @NotNull(message = "여행 시작일은 필수입니다.")
            LocalDate startDate,

            @Schema(description = "여행 종료일", example = "2026-05-24")
            @NotNull(message = "여행 종료일은 필수입니다.")
            LocalDate endDate,

            @Schema(description = "폴더 색상/테마", example = "BASIC")
            String folderTheme,

            @Schema(description = "수정 후 최종 여행 기록 사진 목록")
            @Valid
            @NotEmpty(message = "여행 기록 사진은 최소 1장 이상 등록해야 합니다.")
            @Size(min = 1, max = 5, message = "여행 기록 사진은 최소 1장 이상, 최대 5장까지 등록할 수 있습니다.")
            List<ImageRequest> images,

            @Schema(description = "수정 후 최종 여행 기록 스티커 목록")
            @Valid
            List<StickerRequest> stickers
    ) {
    }

    @Schema(name = "TravelRecordImageRequest", description = "여행 기록 사진 요청")
    public record ImageRequest(
            @Schema(description = "사진 이미지 key", example = "travel-records/1/image-1.jpg")
            @NotBlank(message = "사진 이미지 key는 필수입니다.")
            String imageKey,

            @Schema(description = "사진 순서", example = "1")
            @NotNull(message = "사진 순서는 필수입니다.")
            @Positive(message = "사진 순서는 양수여야 합니다.")
            Integer imageOrder
    ) {
    }

    @Schema(name = "TravelRecordStickerRequest", description = "여행 기록 스티커 요청")
    public record StickerRequest(
            @Schema(description = "스티커 ID", example = "1")
            @NotNull(message = "스티커 ID는 필수입니다.")
            @Positive(message = "스티커 ID는 양수여야 합니다.")
            Long stickerId,

            @Schema(description = "스티커 X 좌표", example = "120.5")
            @NotNull(message = "스티커 X 좌표는 필수입니다.")
            @PositiveOrZero(message = "스티커 X 좌표는 0 이상이어야 합니다.")
            Double positionX,

            @Schema(description = "스티커 Y 좌표", example = "80.0")
            @NotNull(message = "스티커 Y 좌표는 필수입니다.")
            @PositiveOrZero(message = "스티커 Y 좌표는 0 이상이어야 합니다.")
            Double positionY,

            @Schema(description = "스티커 회전값", example = "15.0", defaultValue = "0")
            @NotNull(message = "스티커 회전값은 필수입니다.")
            Double rotation,

            @Schema(description = "스티커 크기 비율", example = "1.2", defaultValue = "1")
            @NotNull(message = "스티커 크기 비율은 필수입니다.")
            @Positive(message = "스티커 크기 비율은 양수여야 합니다.")
            Double scale,

            @Schema(description = "스티커 겹침 순서", example = "1", defaultValue = "0")
            @NotNull(message = "스티커 겹침 순서는 필수입니다.")
            @PositiveOrZero(message = "스티커 겹침 순서는 0 이상이어야 합니다.")
            Integer zIndex
    ) {
    }
}

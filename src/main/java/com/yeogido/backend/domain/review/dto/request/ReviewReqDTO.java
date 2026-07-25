package com.yeogido.backend.domain.review.dto.request;

import com.yeogido.backend.domain.review.enums.ReviewSortType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReviewReqDTO {

    @Schema(name = "ReviewListRequest", description = "최근 후기 목록 조회 요청")
    public record ListRequest(
            @Schema(description = "마지막으로 조회한 리뷰 ID", example = "101")
            @Positive(message = "cursor는 양수여야 합니다.")
            Long cursor,

            @Schema(description = "조회할 리뷰 개수", example = "10", defaultValue = "10")
            @Positive(message = "size는 양수여야 합니다.")
            Integer size,

            @Schema(description = "정렬 기준", example = "LATEST", allowableValues = {"LATEST", "RATING"}, defaultValue = "LATEST")
            ReviewSortType sort
    ) {
    }

    @Schema(name = "ReviewUpdateRequest", description = "추천 코스 리뷰 수정 요청")
    public record UpdateRequest(
            @NotNull(message = "별점은 필수입니다.")
            @Min(value = 1, message = "별점은 1 이상이어야 합니다.")
            @Max(value = 5, message = "별점은 5 이하여야 합니다.")
            @Schema(description = "별점", example = "4")
            Integer rating,

            @Size(max = 300, message = "리뷰 내용은 최대 300자까지 입력할 수 있습니다.")
            @Schema(description = "리뷰 내용", example = "동선이 편해서 여행하기 좋았습니다. 다음에도 이용하고 싶어요.")
            String content,

            @Valid
            @Size(max = 5, message = "리뷰 이미지는 최대 5장까지 등록할 수 있습니다.")
            @Schema(description = "리뷰 이미지 목록. null이면 기존 이미지를 유지하고, 빈 배열이면 기존 이미지를 모두 삭제합니다.")
            List<@NotNull(message = "리뷰 이미지 정보가 올바르지 않습니다.") ReviewImageRequest> images
    ) {
    }

    @Schema(name = "ReviewImageRequest", description = "추천 코스 리뷰 이미지 요청")
    public record ReviewImageRequest(
            @NotBlank(message = "이미지 key는 필수입니다.")
            @Schema(description = "리뷰 이미지 S3 key", example = "reviews/abc.jpg")
            String imageKey,

            @NotNull(message = "이미지 순서는 필수입니다.")
            @Positive(message = "이미지 순서는 양수여야 합니다.")
            @Schema(description = "리뷰 이미지 순서", example = "1")
            Integer imageOrder
    ) {
    }
}

package com.yeogido.backend.domain.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public class ReviewReqDTO {

    @Schema(name = "ReviewListRequest", description = "최근 후기 목록 조회 요청")
    public record ListRequest(
            @Schema(description = "마지막으로 조회한 리뷰 ID", example = "101")
            @Positive(message = "cursor는 양수여야 합니다.")
            Long cursor,

            @Schema(description = "조회할 리뷰 개수", example = "10", defaultValue = "10")
            @Positive(message = "size는 양수여야 합니다.")
            Integer size
    ) {
    }
}

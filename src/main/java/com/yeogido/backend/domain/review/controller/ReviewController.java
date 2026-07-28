package com.yeogido.backend.domain.review.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.domain.review.service.ReviewService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Review", description = "리뷰 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "최근 후기 조회",
            description = "홈 화면에 표시할 최근 등록된 여행 후기 3개를 조회합니다."
    )
    @GetMapping("/recent")
    public ApiResponse<ReviewResDTO.RecentReviewsResponse> getRecentReviews() {
        ReviewResDTO.RecentReviewsResponse result = reviewService.getRecentReviews();
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "최근 후기 목록 조회",
            description = "전체 여행 후기를 최신순 또는 별점순으로 조회합니다."
    )
    @GetMapping
    public ApiResponse<CursorResponse<ReviewResDTO.ReviewDetail>> getReviews(
            @Valid @ModelAttribute ReviewReqDTO.ListRequest request
    ) {
        CursorResponse<ReviewResDTO.ReviewDetail> result = reviewService.getReviews(request);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "후기 수정",
            description = "로그인한 사용자가 본인이 작성한 추천 코스 리뷰의 별점, 내용, 이미지를 수정합니다."
    )
    @PatchMapping("/{reviewId}")
    public ApiResponse<ReviewResDTO.UpdateResponse> updateReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ReviewReqDTO.UpdateRequest request
    ) {
        ReviewResDTO.UpdateResponse result = reviewService.updateReview(
                reviewId,
                authUser.userId(),
                request
        );

        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "후기 삭제",
            description = "로그인한 사용자가 본인이 작성한 추천 코스 리뷰를 삭제합니다."
    )
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        reviewService.deleteReview(reviewId, authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.NO_CONTENT);
    }
}

package com.yeogido.backend.domain.review.service;

import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface ReviewService {

    ReviewResDTO.RecentReviewsResponse getRecentReviews();

    CursorResponse<ReviewResDTO.ReviewDetail> getReviews(
            ReviewReqDTO.ListRequest request
    );
}

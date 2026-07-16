package com.yeogido.backend.domain.review.service;

import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    private static final LocalDateTime SAMPLE_CREATED_AT = LocalDateTime.of(2026, 7, 5, 15, 30);

    public ReviewResDTO.RecentReviewsResponse getRecentReviews() {

        ReviewResDTO.Author author = new ReviewResDTO.Author(
                "민지",
                "TWENTIES",
                "https://example.com/profile.png"
        );

        ReviewResDTO.RecentReview review = new ReviewResDTO.RecentReview(
                101L,
                "지도 동선이 너무 편했어요. 전시 포인트마다 사진 각이 딱 잡혔고, 야경까지 흐름이 좋아서 만족!",
                BigDecimal.valueOf(5.0),
                SAMPLE_CREATED_AT,
                author
        );

        return new ReviewResDTO.RecentReviewsResponse(List.of(review));
    }

    public CursorResponse<ReviewResDTO.ReviewDetail> getReviews(
            ReviewReqDTO.ListRequest request
    ) {
        ReviewResDTO.Author author = new ReviewResDTO.Author(
                "민지",
                "TWENTIES",
                "https://example.com/profile.png"
        );

        ReviewResDTO.Course course = new ReviewResDTO.Course(
                15L,
                "강릉 혼자 여행 코스",
                "https://example.com/course.png",
                DurationType.TWO_NIGHT,
                TransportType.PUBLIC,
                false
        );

        ReviewResDTO.ReviewDetail review = new ReviewResDTO.ReviewDetail(
                101L,
                "지도 동선이 너무 편했어요. 전시 포인트마다 사진 각이 딱 잡혔고, 야경까지 흐름이 좋아서 만족!",
                BigDecimal.valueOf(5.0),
                SAMPLE_CREATED_AT,
                author,
                course
        );

        return CursorResponse.of(
                List.of(review),
                101L,
                10L,
                true
        );
    }
}

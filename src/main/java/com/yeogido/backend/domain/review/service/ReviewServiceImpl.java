package com.yeogido.backend.domain.review.service;

import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.repository.CourseReviewImageRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.review.converter.ReviewConverter;
import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private static final int RECENT_REVIEW_LIMIT = 3;
    private static final LocalDateTime SAMPLE_CREATED_AT = LocalDateTime.of(2026, 7, 5, 15, 30);

    private final CourseReviewRepository courseReviewRepository;
    private final CourseReviewImageRepository courseReviewImageRepository;
    private final S3Service s3Service;

    @Override
    public ReviewResDTO.RecentReviewsResponse getRecentReviews() {
        List<CourseReview> reviews = courseReviewRepository.findRecentReviews(
                PageRequest.of(0, RECENT_REVIEW_LIMIT)
        );

        Map<Long, List<CourseReviewImage>> imageMap = getReviewImageMap(reviews);

        return ReviewConverter.toRecentReviewsResponse(reviews, imageMap, s3Service::getImageUrl);
    }

    @Override
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

    private Map<Long, List<CourseReviewImage>> getReviewImageMap(List<CourseReview> reviews) {
        List<Long> reviewIds = reviews.stream()
                .map(CourseReview::getId)
                .toList();

        if (reviewIds.isEmpty()) {
            return Map.of();
        }

        return courseReviewImageRepository
                .findAllByCourseReview_IdInOrderByCourseReview_IdAscImageOrderAsc(reviewIds).stream()
                .collect(Collectors.groupingBy(image -> image.getCourseReview().getId()));
    }
}

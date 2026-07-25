package com.yeogido.backend.domain.review.service;

import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewImageRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.review.converter.ReviewConverter;
import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.domain.review.enums.ReviewSortType;
import com.yeogido.backend.domain.review.exception.ReviewErrorCode;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private static final Long MOCK_USER_ID = 1L;
    private static final int RECENT_REVIEW_LIMIT = 3;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final CourseReviewRepository courseReviewRepository;
    private final CourseReviewImageRepository courseReviewImageRepository;
    private final CourseLikeRepository courseLikeRepository;
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
        int size = resolveSize(request.size());
        ReviewSortType sort = resolveSort(request.sort());

        Pageable pageable = PageRequest.of(0, size + 1);
        List<CourseReview> reviews = findReviews(
                request.cursor(),
                sort,
                pageable
        );

        boolean hasNext = reviews.size() > size;
        List<CourseReview> content = hasNext
                ? reviews.subList(0, size)
                : reviews;

        Map<Long, List<CourseReviewImage>> imageMap = getReviewImageMap(content);
        Set<Long> likedCourseIds = getLikedCourseIds(content);

        List<ReviewResDTO.ReviewDetail> items = ReviewConverter.toReviewDetails(
                content,
                imageMap,
                likedCourseIds,
                s3Service::getImageUrl
        );

        Object cursorValue = getNextCursorValue(content, sort);
        Long cursorId = content.isEmpty()
                ? null
                : content.get(content.size() - 1).getId();

        return CursorResponse.of(items, cursorValue, cursorId, hasNext);
    }

    @Override
    @Transactional
    public ReviewResDTO.UpdateResponse updateReview(
            Long reviewId,
            Long userId,
            ReviewReqDTO.UpdateRequest request
    ) {
        CourseReview review = courseReviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ReviewErrorCode.REVIEW_NOT_FOUND));

        validateReviewOwner(review, userId);

        review.update(request.rating(), request.content());
        updateImagesIfRequested(review, request.images());

        return ReviewConverter.toUpdateResponse(review);
    }

    private List<CourseReview> findReviews(
            Long cursor,
            ReviewSortType sort,
            Pageable pageable
    ) {
        if (cursor == null) {
            return findFirstPage(sort, pageable);
        }

        CourseReview cursorReview = courseReviewRepository.findById(cursor)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_REQUEST));

        if (sort == ReviewSortType.RATING) {
            return courseReviewRepository.findReviewsOrderByRatingAfterCursor(
                    cursorReview.getRating(),
                    cursorReview.getId(),
                    pageable
            );
        }

        return courseReviewRepository.findReviewsOrderByLatestAfterCursor(
                cursorReview.getCreatedAt(),
                cursorReview.getId(),
                pageable
        );
    }

    private List<CourseReview> findFirstPage(ReviewSortType sort, Pageable pageable) {
        if (sort == ReviewSortType.RATING) {
            return courseReviewRepository.findReviewsOrderByRating(pageable);
        }

        return courseReviewRepository.findReviewsOrderByLatest(pageable);
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

    private Set<Long> getLikedCourseIds(List<CourseReview> reviews) {
        List<Long> courseIds = reviews.stream()
                .map(review -> review.getCourse().getId())
                .distinct()
                .toList();

        if (courseIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(courseLikeRepository.findLikedCourseIdsByUserIdAndCourseIdIn(
                getCurrentUserId(),
                courseIds
        ));
    }

    private void validateReviewOwner(CourseReview review, Long userId) {
        if (!review.getUser().getId().equals(userId)) {
            throw new GeneralException(ReviewErrorCode.REVIEW_ACCESS_DENIED);
        }
    }

    private void updateImagesIfRequested(
            CourseReview review,
            List<ReviewReqDTO.ReviewImageRequest> images
    ) {
        if (images == null) {
            return;
        }

        validateImageOrder(images);

        courseReviewImageRepository.deleteAllByCourseReview_Id(review.getId());

        if (images.isEmpty()) {
            return;
        }

        courseReviewImageRepository.saveAll(
                ReviewConverter.toCourseReviewImages(review, images)
        );
    }

    private void validateImageOrder(List<ReviewReqDTO.ReviewImageRequest> images) {
        Set<Integer> imageOrders = new HashSet<>();

        for (ReviewReqDTO.ReviewImageRequest image : images) {
            if (!imageOrders.add(image.imageOrder())) {
                throw new GeneralException(ReviewErrorCode.DUPLICATE_IMAGE_ORDER);
            }
        }

        for (int order = 1; order <= images.size(); order++) {
            if (!imageOrders.contains(order)) {
                throw new GeneralException(ReviewErrorCode.INVALID_IMAGE_ORDER);
            }
        }
    }

    private Long getCurrentUserId() {
        // TODO: Spring Security 적용 후 인증 사용자 ID로 교체
        return MOCK_USER_ID;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }

        return size;
    }

    private ReviewSortType resolveSort(ReviewSortType sort) {
        if (sort == null) {
            return ReviewSortType.LATEST;
        }

        return sort;
    }

    private Object getNextCursorValue(List<CourseReview> reviews, ReviewSortType sort) {
        if (reviews.isEmpty()) {
            return null;
        }

        CourseReview lastReview = reviews.get(reviews.size() - 1);

        if (sort == ReviewSortType.RATING) {
            return lastReview.getRating();
        }

        return lastReview.getCreatedAt();
    }
}

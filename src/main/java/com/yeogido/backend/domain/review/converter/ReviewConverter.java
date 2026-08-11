package com.yeogido.backend.domain.review.converter;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewConverter {

    public static ReviewResDTO.RecentReviewsResponse toRecentReviewsResponse(
            List<CourseReview> reviews,
            Map<Long, List<CourseReviewImage>> imageMap,
            Function<String, String> imageUrlResolver
    ) {
        List<ReviewResDTO.RecentReview> recentReviews = reviews.stream()
                .map(review -> toRecentReview(
                        review,
                        imageMap.getOrDefault(review.getId(), List.of()),
                        imageUrlResolver
                ))
                .toList();

        return new ReviewResDTO.RecentReviewsResponse(recentReviews);
    }

    public static List<ReviewResDTO.ReviewDetail> toReviewDetails(
            List<CourseReview> reviews,
            Map<Long, List<CourseReviewImage>> imageMap,
            Set<Long> likedCourseIds,
            Map<Long, List<String>> courseTagMap,
            Long currentUserId,
            Function<String, String> imageUrlResolver
    ) {
        return reviews.stream()
                .map(review -> toReviewDetail(
                        review,
                        imageMap.getOrDefault(review.getId(), List.of()),
                        likedCourseIds,
                        courseTagMap,
                        currentUserId,
                        imageUrlResolver
                ))
                .toList();
    }

    public static ReviewResDTO.UpdateResponse toUpdateResponse(CourseReview review) {
        return new ReviewResDTO.UpdateResponse(review.getId());
    }

    public static List<CourseReviewImage> toCourseReviewImages(
            CourseReview review,
            List<ReviewReqDTO.ReviewImageRequest> images
    ) {
        return images.stream()
                .map(image -> CourseReviewImage.builder()
                        .courseReview(review)
                        .imageKey(image.imageKey())
                        .imageOrder(image.imageOrder())
                        .build())
                .toList();
    }

    private static ReviewResDTO.RecentReview toRecentReview(
            CourseReview review,
            List<CourseReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return new ReviewResDTO.RecentReview(
                review.getId(),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt(),
                toReviewImages(images, imageUrlResolver),
                toAuthor(review.getUser(), imageUrlResolver)
        );
    }

    private static ReviewResDTO.ReviewDetail toReviewDetail(
            CourseReview review,
            List<CourseReviewImage> images,
            Set<Long> likedCourseIds,
            Map<Long, List<String>> courseTagMap,
            Long currentUserId,
            Function<String, String> imageUrlResolver
    ) {
        Course course = review.getCourse();

        return new ReviewResDTO.ReviewDetail(
                review.getId(),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt(),
                isMine(review, currentUserId),
                toReviewImages(images, imageUrlResolver),
                toAuthor(review.getUser(), imageUrlResolver),
                toCourse(
                        course,
                        likedCourseIds.contains(course.getId()),
                        courseTagMap.getOrDefault(course.getId(), List.of()),
                        imageUrlResolver
                )
        );
    }

    private static boolean isMine(CourseReview review, Long currentUserId) {
        return currentUserId != null
                && review.getUser().getId().equals(currentUserId);
    }

    private static List<ReviewResDTO.ReviewImage> toReviewImages(
            List<CourseReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return images.stream()
                .map(image -> new ReviewResDTO.ReviewImage(
                        image.getImageKey(),
                        imageUrlResolver.apply(image.getImageKey()),
                        image.getImageOrder()
                ))
                .toList();
    }

    private static ReviewResDTO.Author toAuthor(
            User user,
            Function<String, String> imageUrlResolver
    ) {
        return new ReviewResDTO.Author(
                user.getNickname(),
                user.getAgeGroup(),
                user.getGender(),
                imageUrlResolver.apply(user.getProfileImage())
        );
    }

    private static ReviewResDTO.Course toCourse(
            Course course,
            boolean isLiked,
            List<String> tags,
            Function<String, String> imageUrlResolver
    ) {
        return new ReviewResDTO.Course(
                course.getId(),
                course.getCourseType(),
                course.getTitle(),
                imageUrlResolver.apply(course.getThumbnailKey()),
                course.getDurationType(),
                course.getTransportType(),
                course.getCompanionType(),
                tags,
                isLiked
        );
    }
}

package com.yeogido.backend.domain.review.converter;

import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import com.yeogido.backend.domain.review.dto.response.ReviewResDTO;
import com.yeogido.backend.domain.user.entity.User;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

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
                toAuthor(review.getUser())
        );
    }

    private static List<ReviewResDTO.ReviewImage> toReviewImages(
            List<CourseReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return images.stream()
                .map(image -> new ReviewResDTO.ReviewImage(
                        imageUrlResolver.apply(image.getImageKey()),
                        image.getImageOrder()
                ))
                .toList();
    }

    private static ReviewResDTO.Author toAuthor(User user) {
        return new ReviewResDTO.Author(
                user.getNickname(),
                resolveAgeGroup(user.getBirthYear()),
                user.getProfileImage()
        );
    }

    private static String resolveAgeGroup(String birthYear) {
        if (!StringUtils.hasText(birthYear)) {
            return null;
        }

        try {
            int age = Year.now().getValue() - Integer.parseInt(birthYear) + 1;

            if (age < 20) {
                return "TEEN";
            }

            if (age < 30) {
                return "TWENTIES";
            }

            if (age < 40) {
                return "THIRTIES";
            }

            if (age < 50) {
                return "FORTIES";
            }

            return "FIFTIES_PLUS";
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.yeogido.backend.domain.course.converter;

import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.*;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.entity.PlaceOperatingDay;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CourseConverter {

    public static Course toCourse(
            CourseReqDTO.CourseCreateReq request,
            User user,
            Region region,
            CourseType courseType
    ) {
        return Course.builder()
                .user(user)
                .region(region)
                .title(request.title())
                .description(request.description())
                .courseType(courseType)
                .recommendOrder(defaultRecommendOrder(courseType))
                .durationType(request.durationType())
                .transportType(request.transportType())
                .companionType(request.companionType())
                .monthStart(request.monthStart())
                .monthEnd(request.monthEnd())
                .thumbnailKey(request.thumbnailKey())
                .routeImageKey(request.routeImageKey())
                .build();
    }

    private static Integer defaultRecommendOrder(CourseType courseType) {
        return courseType == CourseType.OFFICIAL ? 0 : null;
    }

    public static Place toPlace(CourseReqDTO.CourseItemCreateReq request, Region region) {
        return Place.builder()
                .region(region)
                .externalPlaceId(request.externalPlaceId())
                .source(PlaceSource.KAKAO)
                .name(request.name())
                .categoryGroupCode(request.categoryGroupCode())
                .roadAddress(request.roadAddress())
                .lotAddress(request.lotAddress())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
    }

    public static CourseHashtag toCourseHashtag(Course course, Hashtag hashtag) {
        return CourseHashtag.builder()
                .course(course)
                .hashtag(hashtag)
                .build();
    }

    public static CourseItem toPlaceCourseItem(
            Course course,
            Place place,
            CourseReqDTO.CourseItemCreateReq request
    ) {
        return CourseItem.builder()
                .course(course)
                .place(place)
                .itemType(CourseItemType.PLACE)
                .orderNo(request.order())
                .imageKey(request.imageKey())
                .build();
    }

    public static CourseItem toContentCourseItem(
            Course course,
            Content content,
            CourseReqDTO.CourseItemCreateReq request
    ) {
        return CourseItem.builder()
                .course(course)
                .content(content)
                .itemType(CourseItemType.CONTENT)
                .orderNo(request.order())
                .build();
    }

    public static CourseLike toCourseLike(User user, Course course) {
        return CourseLike.builder()
                .user(user)
                .course(course)
                .build();
    }

    public static PlaceOperatingDay toPlaceOperatingDay(
            Place place,
            CourseReqDTO.PlaceOperatingDayReq request
    ) {
        return PlaceOperatingDay.builder()
                .place(place)
                .dayOfWeek(request.dayOfWeek())
                .openTime(request.openTime())
                .closeTime(request.closeTime())
                .build();
    }

    public static CourseItemTime toCourseItemTime(
            CourseItem fromCourseItem,
            CourseItem toCourseItem,
            CourseReqDTO.CourseItemTimeReq request
    ) {
        return CourseItemTime.builder()
                .fromCourseItem(fromCourseItem)
                .toCourseItem(toCourseItem)
                .transportMode(request.transportMode())
                .durationMinutes(request.durationMinutes())
                .build();
    }

    public static CourseResDTO.CourseDetail toCourseDetail(
            Course course,
            String thumbnailUrl,
            List<String> tags,
            boolean isLiked,
            boolean canManage,
            List<CourseResDTO.CourseItem> courseItems,
            String profileImageUrl
    ) {
        return new CourseResDTO.CourseDetail(
                course.getId(),
                course.getCourseType(),
                course.getTitle(),
                thumbnailUrl,
                course.getDescription(),
                tags,
                course.getDurationType(),
                course.getTransportType(),
                course.getMonthStart(),
                course.getMonthEnd(),
                course.getCompanionType(),
                isLiked,
                canManage,
                courseItems,
                toAuthor(course, profileImageUrl)
        );
    }

    public static CourseResDTO.CourseItem toCourseItem(
            CourseItem courseItem,
            boolean isLiked,
            Function<String, String> imageUrlResolver,
            List<CourseResDTO.OperatingDay> operatingDays,
            List<CourseResDTO.CourseItemTime> timesFromPrevious
    ) {
        Place place = resolvePlace(courseItem);
        Content content = courseItem.getContent();

        if (courseItem.getItemType() == CourseItemType.PLACE) {
            String imageKey = courseItem.getImageKey();

            return new CourseResDTO.PlaceCourseItem(
                    courseItem.getId(),
                    courseItem.getOrderNo(),
                    courseItem.getItemType(),
                    place.getId(),
                    isLiked,
                    place.getSource(),
                    place.getExternalPlaceId(),
                    place.getCategoryGroupCode(),
                    place.getName(),
                    place.getRoadAddress(),
                    place.getLotAddress(),
                    place.getLatitude(),
                    place.getLongitude(),
                    imageKey,
                    imageUrlResolver.apply(imageKey),
                    operatingDays,
                    timesFromPrevious
            );
        }

        String imageKey = content.getThumbnailImage();

        return new CourseResDTO.ContentCourseItem(
                courseItem.getId(),
                courseItem.getOrderNo(),
                courseItem.getItemType(),
                content.getId(),
                isLiked,
                content.getEventStatus(),
                place.getSource(),
                place.getExternalPlaceId(),
                place.getCategoryGroupCode(),
                content.getTitle(),
                place.getRoadAddress(),
                place.getLotAddress(),
                place.getLatitude(),
                place.getLongitude(),
                imageUrlResolver.apply(imageKey),
                timesFromPrevious
        );
    }

    public static CourseResDTO.OperatingDay toOperatingDay(PlaceOperatingDay operatingDay) {
        return new CourseResDTO.OperatingDay(
                operatingDay.getDayOfWeek(),
                operatingDay.getOpenTime(),
                operatingDay.getCloseTime()
        );
    }

    public static CourseResDTO.CourseItemTime toCourseItemTime(CourseItemTime itemTime) {
        return new CourseResDTO.CourseItemTime(
                itemTime.getTransportMode(),
                itemTime.getDurationMinutes()
        );
    }

    private static Place resolvePlace(CourseItem courseItem) {
        if (courseItem.getItemType() == CourseItemType.PLACE) {
            return courseItem.getPlace();
        }

        return courseItem.getContent().getPlace();
    }

    private static CourseResDTO.Author toAuthor(
            Course course,
            String profileImageUrl
    ) {
        if (course.getCourseType() != CourseType.LOCAL || course.getUser() == null) {
            return null;
        }

        return new CourseResDTO.Author(
                course.getUser().getNickname(),
                profileImageUrl
        );
    }

    public static CourseResDTO.CourseSummary toCourseSummary(
            CourseRepository.CourseSummaryProjection summary,
            String thumbnailUrl
    ) {
        return new CourseResDTO.CourseSummary(
                summary.getCourseId(),
                summary.getTitle(),
                thumbnailUrl,
                summary.getDurationType(),
                summary.getTransportType(),
                summary.getCompanionType()
        );
    }

    public static CourseReview toCourseReview(
            CourseReqDTO.ReviewCreateReq request,
            User user,
            Course course
    ) {
        return CourseReview.builder()
                .user(user)
                .course(course)
                .rating(request.rating())
                .content(request.content())
                .build();
    }

    public static CourseReviewImage toCourseReviewImage(
            CourseReview courseReview,
            CourseReqDTO.ReviewImageReq imageReq
    ) {
        return CourseReviewImage.builder()
                .courseReview(courseReview)
                .imageKey(imageReq.imageKey())
                .imageOrder(imageReq.order())
                .build();
    }

    public static CourseResDTO.CourseLocalPopularPreview toLocalPopularCoursePreview(
            CourseRepository.CourseLocalPopularProjection course,
            String thumbnailUrl,
            String routeImageUrl,
            String profileImageUrl,
            List<String> tags,
            boolean isLiked
    ) {
        return new CourseResDTO.CourseLocalPopularPreview(
                course.getCourseId(),
                thumbnailUrl,
                routeImageUrl,
                course.getTitle(),
                course.getDurationType(),
                course.getCompanionType(),
                new CourseResDTO.LocalPopularAuthor(
                        course.getUserId(),
                        course.getNickname(),
                        profileImageUrl
                ),
                course.getCreatedAt(),
                tags,
                isLiked
        );
    }

    public static List<CourseResDTO.ReviewPreview> toReviewPreviews(
            List<CourseReview> reviews,
            Map<Long, List<CourseReviewImage>> imageMap,
            Long currentUserId,
            Function<String, String> imageUrlResolver
    ) {
        return reviews.stream()
                .map(review -> toReviewPreview(
                        review,
                        imageMap.getOrDefault(review.getId(), List.of()),
                        currentUserId,
                        imageUrlResolver
                ))
                .toList();
    }

    private static CourseResDTO.ReviewPreview toReviewPreview(
            CourseReview review,
            List<CourseReviewImage> images,
            Long currentUserId,
            Function<String, String> imageUrlResolver
    ) {
        return new CourseResDTO.ReviewPreview(
                review.getId(),
                toReviewAuthor(review.getUser(), imageUrlResolver),
                review.getRating(),
                review.getContent(),
                isMine(review, currentUserId),
                toReviewImages(images, imageUrlResolver),
                review.getCreatedAt().toLocalDate()
        );
    }

    private static boolean isMine(CourseReview review, Long currentUserId) {
        return currentUserId != null
                && review.getUser().getId().equals(currentUserId);
    }

    private static CourseResDTO.ReviewAuthor toReviewAuthor(
            User user,
            Function<String, String> imageUrlResolver
    ) {
        return new CourseResDTO.ReviewAuthor(
                user.getNickname(),
                user.getAgeGroup(),
                user.getGender(),
                imageUrlResolver.apply(user.getProfileImage())
        );
    }

    private static List<CourseResDTO.ReviewImage> toReviewImages(
            List<CourseReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return images.stream()
                .map(image -> new CourseResDTO.ReviewImage(
                        image.getImageKey(),
                        imageUrlResolver.apply(image.getImageKey()),
                        image.getImageOrder()
                ))
                .toList();
    }

    public static CourseResDTO.CourseRecommendedPreview toRecommendedCoursePreview(
            CourseRepository.CourseRecommendedProjection course,
            String thumbnailUrl
    ) {
        return new CourseResDTO.CourseRecommendedPreview(
                course.getCourseId(),
                course.getTitle(),
                course.getDescription(),
                thumbnailUrl,
                course.getDurationType(),
                course.getTransportType()
        );
    }

    public static UserResDTO.MyCourseResponse toMyCourseResponse(
            Course course,
            String thumbnailUrl,
            String routeImageUrl,
            List<String> hashtags
    ) {
        return new UserResDTO.MyCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                thumbnailUrl,
                routeImageUrl,
                course.getDurationType().name(),
                course.getTransportType().name(),
                course.getCompanionType().name(),
                hashtags,
                course.getCreatedAt()
        );
    }

    public static UserResDTO.MyReviewResponse toMyReviewResponse(
            CourseReview review,
            String profileImageUrl,
            List<CourseReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        User user = review.getUser();

        return new UserResDTO.MyReviewResponse(
                review.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getAgeGroup(),
                user.getGender(),
                review.getRating(),
                review.getContent(),
                toMyReviewImages(images, imageUrlResolver),
                review.getCreatedAt()
        );
    }

    private static List<UserResDTO.MyReviewImage> toMyReviewImages(
            List<CourseReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return images.stream()
                .map(image -> new UserResDTO.MyReviewImage(
                        image.getImageKey(),
                        imageUrlResolver.apply(image.getImageKey()),
                        image.getImageOrder()
                ))
                .toList();
    }


    public static UserResDTO.MyPostResponse toMyPostResponse(
            UserResDTO.MyCourseResponse course,
            UserResDTO.MyReviewResponse review,
            BusinessPromotionResponse.MySummary promotion
    ) {
        return new UserResDTO.MyPostResponse(
                course,
                review,
                promotion
        );
    }



}

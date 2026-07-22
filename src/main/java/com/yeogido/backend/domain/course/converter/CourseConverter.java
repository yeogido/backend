package com.yeogido.backend.domain.course.converter;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseHashtag;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CourseConverter {

    public static Course toCourse(CourseReqDTO.CourseCreateReq request, User user, Region region) {
        return Course.builder()
                .user(user)
                .region(region)
                .title(request.title())
                .description(request.description())
                .courseType(CourseType.LOCAL)
                .durationType(request.durationType())
                .transportType(request.transportType())
                .companionType(request.companionType())
                .monthStart(request.monthStart())
                .monthEnd(request.monthEnd())
                .thumbnailKey(request.thumbnailKey())
                .build();
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

    public static CourseResDTO.CourseDetail toCourseDetail(
            Course course,
            String thumbnailUrl,
            List<String> tags,
            boolean isLiked,
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
                courseItems,
                toAuthor(course, profileImageUrl)
        );
    }

    public static CourseResDTO.CourseItem toCourseItem(CourseItem courseItem) {
        Place place = resolvePlace(courseItem);
        Content content = courseItem.getContent();

        return new CourseResDTO.CourseItem(
                courseItem.getOrderNo(),
                courseItem.getItemType(),
                place.getSource(),
                place.getExternalPlaceId(),
                content == null ? place.getName() : content.getTitle(),
                place.getRoadAddress(),
                place.getLotAddress(),
                place.getLatitude(),
                place.getLongitude()
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
}

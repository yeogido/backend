package com.yeogido.backend.domain.course.converter;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseHashtag;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
}

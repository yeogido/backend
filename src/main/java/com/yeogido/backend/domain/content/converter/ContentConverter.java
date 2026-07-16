package com.yeogido.backend.domain.content.converter;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.place.entity.Place;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentConverter {
    public static ContentResDTO.ContentInfo toContentInfo(
            Content content,
            Long likeCount
    ) {
        // TODO : 반환값에 해시태그 추가
        return new ContentResDTO.ContentInfo(
                content.getId(),
                content.getPlace().getId(),
                content.getTitle(),
                content.getThumbnailImage(),
                content.getPlace().getRegion().getName(),
                likeCount,
                content.getStartDate(),
                content.getEndDate()
        );
    }

    public static ContentResDTO.ContentInfo toContentInfo(
            Tuple tuple,
            QContent qContent,
            NumberExpression<Long> likeCountExpression
    ) {
        Content content = tuple.get(qContent);
        Long likeCount = tuple.get(likeCountExpression);

        return toContentInfo(content, likeCount);
    }

    public static ContentResDTO.PlaceInfo toPlaceInfo(
            Place place
    ){
        return new ContentResDTO.PlaceInfo(
                place.getId(),
                place.getName(),
                place.getRoadAddress(),
                place.getLatitude(),
                place.getLongitude()
        );
    }

    public static ContentResDTO.CourseInfo toCourseInfo(
            Course course,
            boolean liked
    ) {
        return new ContentResDTO.CourseInfo(
                course.getId(),
                course.getTitle(),
                course.getThumbnailKey(),
                course.getDescription(),
                course.getDurationType(),
                course.getTransportType(),
                course.getCompanionType(),
                liked
        );
    }

    public static ContentResDTO.ContentDetailRes toContentDetailRes(
            Content content,
            List<Long> hashtagIds,
            long likeCount,
            boolean liked,
            ContentResDTO.PlaceInfo placeInfo,
            List<ContentResDTO.CourseInfo> courses
    ) {
        return new ContentResDTO.ContentDetailRes(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailImage(),
                hashtagIds,
                content.getStartDate(),
                content.getEndDate(),
                likeCount,
                liked,
                content.getContactPhone(),
                content.getOfficialUrl(),
                placeInfo,
                courses
        );
    }



}

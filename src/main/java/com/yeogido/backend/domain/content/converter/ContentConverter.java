package com.yeogido.backend.domain.content.converter;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.course.entity.Course;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class  ContentConverter {
    public static ContentResDTO.ContentInfo toContentInfo(
            Content content,
            String thumbnailImageUrl,
            Long likeCount,
            List<String> hashtags,
            boolean isLiked
    ) {
        return new ContentResDTO.ContentInfo(
                content.getId(),
                content.getPlace().getId(),
                content.getPlace().getLatitude(),
                content.getPlace().getLongitude(),
                content.getTitle(),
                content.getCategory(),
                thumbnailImageUrl,
                content.getPlace().getRegion().getFullName(),
                hashtags,
                likeCount,
                isLiked,
                content.getStartDate(),
                content.getEndDate()
        );
    }

    public static ContentResDTO.ContentInfo toContentInfo(
            Tuple tuple,
            QContent qContent,
            NumberExpression<Long> likeCountExpression,
            Map<Long, List<String>> hashtagMap,
            String thumbnailImageUrl,
            Set<Long> likedContentIds
    ) {
        Content content = tuple.get(qContent);
        Long likeCount = tuple.get(likeCountExpression);

        return toContentInfo(
                content,
                thumbnailImageUrl,
                likeCount,
                hashtagMap.getOrDefault(content.getId(), List.of()),
                likedContentIds.contains(content.getId()));
    }

    public static Place toPlace(
            ContentReqDTO.PlaceReq request,
            Region region
    ) {
        return Place.builder()
                .region(region)
                .externalPlaceId(request.externalPlaceId())
                .source(request.source())
                .name(request.name())
                .roadAddress(request.roadAddress())
                .lotAddress(request.lotAddress())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
    }

    public static Content toContent(
            ContentReqDTO.ContentCreateReq request,
            Place place
    ) {
        ContentSource contentSource =
                switch (request.place().source()) {
                    case KAKAO -> ContentSource.ADMIN;
                    case TOUR_API -> ContentSource.TOUR_API;
                };


        return Content.builder()
                .place(place)
                .externalContentId(request.place().externalPlaceId())
                .source(contentSource)
                .title(request.title())
                .description(request.description())
                .thumbnailImage(request.thumbnailImageKey())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .contactPhone(request.contactPhone())
                .officialUrl(request.officialUrl())
                .category(request.category())
                .build();
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
            String thumbnailImageUrl,
            boolean liked
    ) {
        return new ContentResDTO.CourseInfo(
                course.getId(),
                course.getTitle(),
                thumbnailImageUrl,
                course.getDescription(),
                course.getDurationType(),
                course.getTransportType(),
                course.getCompanionType(),
                liked
        );
    }

    public static ContentResDTO.ContentDetailRes toContentDetailRes(
            Content content,
            String thumbnailImageUrl,
            List<String> hashtags,
            boolean liked,
            ContentResDTO.PlaceInfo placeInfo,
            List<ContentResDTO.CourseInfo> courses
    ) {
        return new ContentResDTO.ContentDetailRes(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                thumbnailImageUrl,
                hashtags,
                content.getStartDate(),
                content.getEndDate(),
                liked,
                content.getContactPhone(),
                content.getOfficialUrl(),
                placeInfo,
                courses
        );
    }

    public static ContentResDTO.BannerRes toBannerRes(
            Content content,
            String thumbnailImageUrl
    ) {
        return new ContentResDTO.BannerRes(
                content.getId(),
                content.getTitle(),
                thumbnailImageUrl,
                content.getDescription(),
                content.getStartDate(),
                content.getEndDate()
        );
    }

    public static ContentResDTO.OngoingContentRes toOngoingContentRes(
            Content content,
            String thumbnailImageUrl,
            List<String> hashtags,
            boolean liked
    ) {
        return new ContentResDTO.OngoingContentRes(
                content.getId(),
                content.getTitle(),
                thumbnailImageUrl,
                content.getStartDate(),
                content.getEndDate(),
                content.getPlace().getRegion().getFullName(),
                hashtags,
                liked
        );
    }



}

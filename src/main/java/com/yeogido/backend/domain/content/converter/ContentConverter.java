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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class  ContentConverter {
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

    public static Place toPlace(
            ContentReqDTO.PlaceReq request,
            Region region
    ) {
        return Place.builder()
                .region(region)
                .externalPlaceId(request.externalPlaceId())
                .source(PlaceSource.valueOf(request.source()))
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
                switch (PlaceSource.valueOf(request.place().source())) {
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
                .category(ContentCategory.valueOf(request.category()))
                .build();
    }
}

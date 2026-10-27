package com.yeogido.backend.domain.placeenrichment.converter;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceReqDTO;
import com.yeogido.backend.domain.region.entity.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TourPlaceConverter {

    public static Place toPlace(TourPlaceReqDTO.Input input) {
        return Place.builder()
                .externalPlaceId(input.externalPlaceId())
                .name(input.name())
                .source(PlaceSource.KAKAO)
                .region(input.regionId() == null ? null : Region.builder().id(input.regionId()).build())
                .categoryGroupCode(input.categoryGroupCode())
                .roadAddress(input.roadAddress())
                .lotAddress(input.lotAddress())
                .latitude(input.latitude())
                .longitude(input.longitude())
                .build();
    }
}

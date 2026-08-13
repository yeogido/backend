package com.yeogido.backend.domain.place.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlaceSource {

    KAKAO("카카오맵"),
    TOUR_API("한국관광공사");

    private final String description;
}
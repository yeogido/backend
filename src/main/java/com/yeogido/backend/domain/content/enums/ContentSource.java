package com.yeogido.backend.domain.content.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContentSource {
    TOUR_API,
    KAKAO_API,
    ADMIN
}
package com.yeogido.backend.domain.file.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageDirectory {
    COURSE("courses"),
    CONTENT("contents"),
    BUSINESS("businesses"),
    PROFILE("profiles"),
    TRAVEL_RECORD("travel-records");

    private final String path;
}

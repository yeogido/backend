package com.yeogido.backend.global.common.dto;

public record NextCursor<T>(
        T cursorValue,
        Long cursorId
) {}
package com.yeogido.backend.global.common;

public record ValidationError(
        String field,
        Object rejectedValue,
        String reason
) {
}

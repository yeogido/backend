package com.yeogido.backend.global.common.response;

public record ValidationError(
        String field,
        Object rejectedValue,
        String reason
) {
}

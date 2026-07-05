package com.yeogido.backend.global.common;

import java.util.List;

public record ValidationErrorResponse(
        List<ValidationError> errors
) {
}

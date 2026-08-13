package com.yeogido.backend.global.common.response;

import java.util.List;

public record ValidationErrorResponse(
        List<ValidationError> errors
) {
}

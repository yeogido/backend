package com.yeogido.backend.domain.file.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {

    INVALID_IMAGE_KEY(
            HttpStatus.BAD_REQUEST,
            "FILE4001",
            "업로드되지 않았거나 유효하지 않은 이미지 키입니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

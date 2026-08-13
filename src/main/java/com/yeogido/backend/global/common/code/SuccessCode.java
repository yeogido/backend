package com.yeogido.backend.global.common.code;

import com.yeogido.backend.global.common.response.BaseResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseResponse {

    OK(
            HttpStatus.OK,
            "COMMON200",
            "요청에 성공했습니다."
    ),

    CREATED(
            HttpStatus.CREATED,
            "COMMON201",
            "생성에 성공했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

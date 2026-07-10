package com.yeogido.backend.global.common.code;

import com.yeogido.backend.global.common.response.BaseResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseResponse {

    OK(
            "COMMON200",
            "요청에 성공했습니다."
    ),

    CREATED(
            "COMMON201",
            "생성에 성공했습니다."
    ),

    NO_CONTENT(
            "COMMON204",
            "삭제에 성공했습니다."
    );

    private final String code;
    private final String message;
}
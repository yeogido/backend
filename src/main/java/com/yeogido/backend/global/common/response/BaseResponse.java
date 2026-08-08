package com.yeogido.backend.global.common.response;

import org.springframework.http.HttpStatus;

public interface BaseResponse {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();

}

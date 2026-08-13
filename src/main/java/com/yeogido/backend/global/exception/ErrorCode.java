package com.yeogido.backend.global.exception;

import com.yeogido.backend.global.common.response.BaseResponse;
import org.springframework.http.HttpStatus;

public interface ErrorCode extends BaseResponse {

    HttpStatus getHttpStatus();

}
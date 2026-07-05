package com.yeogido.backend.global.exception;

import com.yeogido.backend.global.common.BaseResponse;
import org.springframework.http.HttpStatus;

public interface ErrorCode extends BaseResponse {

    HttpStatus getHttpStatus();

}
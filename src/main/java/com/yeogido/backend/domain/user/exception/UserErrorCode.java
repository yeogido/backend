package com.yeogido.backend.domain.user.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

  EMAIL_DUPLICATED(
    HttpStatus.CONFLICT,
    "USER4091",
    "이미 가입된 이메일입니다."
  );

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}

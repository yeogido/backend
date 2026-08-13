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
  ),

  USER_NOT_FOUND(
    HttpStatus.NOT_FOUND,
    "USER4041",
    "존재하지 않는 회원입니다."
  ),

  USER_WITHDRAWN(
    HttpStatus.GONE,
    "USER4101",
    "이미 탈퇴한 회원입니다."
  ),

  FORBIDDEN(
          HttpStatus.FORBIDDEN,
        "USER4031",
                "관리자 권한이 필요합니다."
  );


  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}

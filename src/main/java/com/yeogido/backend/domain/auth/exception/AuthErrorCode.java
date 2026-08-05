package com.yeogido.backend.domain.auth.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

  UNSUPPORTED_SOCIAL_PROVIDER(
    HttpStatus.BAD_REQUEST,
    "EXT4001",
    "지원하지 않는 소셜 플랫폼입니다."
  ),

  SOCIAL_EMAIL_REQUIRED(
    HttpStatus.BAD_REQUEST,
    "AUTH4002",
    "소셜 계정 이메일 제공 동의가 필요합니다."
  ),

  INVALID_SOCIAL_ACCESS_TOKEN(
    HttpStatus.UNAUTHORIZED,
    "AUTH4011",
    "유효하지 않은 소셜 accessToken입니다."
  ),

  INVALID_SOCIAL_LOGIN_REQUEST(
    HttpStatus.BAD_REQUEST,
    "AUTH4005",
    "소셜 로그인 요청 값이 올바르지 않습니다."
  ),

  INVALID_SOCIAL_AUTHORIZATION_CODE(
    HttpStatus.UNAUTHORIZED,
    "AUTH4015",
    "유효하지 않은 소셜 authorization code입니다."
  ),

  INVALID_SOCIAL_SIGNUP_TOKEN(
    HttpStatus.BAD_REQUEST,
    "AUTH4003",
    "유효하지 않거나 만료된 소셜 회원가입 임시 토큰입니다."
  ),

  SOCIAL_ACCOUNT_ALREADY_EXISTS(
    HttpStatus.CONFLICT,
    "AUTH4091",
    "이미 가입된 소셜 계정입니다."
  ),

  PASSWORD_MISMATCH(
    HttpStatus.UNAUTHORIZED,
    "AUTH4012",
    "비밀번호가 일치하지 않습니다."
  ),

  INVALID_REFRESH_TOKEN(
    HttpStatus.UNAUTHORIZED,
    "AUTH4013",
    "유효하지 않거나 만료된 Refresh Token입니다."
  ),

  INVALID_PASSWORD_RESET_CODE(
    HttpStatus.BAD_REQUEST,
    "AUTH4004",
    "인증번호가 일치하지 않거나 만료되었습니다."
  ),

  INVALID_PASSWORD_RESET_TOKEN(
    HttpStatus.UNAUTHORIZED,
    "AUTH4014",
    "유효하지 않거나 만료된 비밀번호 재설정 토큰입니다."
  ),

  LOGIN_REQUIRED(
    HttpStatus.UNAUTHORIZED,
    "AUTH4011",
    "로그인이 필요합니다."
  );

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}

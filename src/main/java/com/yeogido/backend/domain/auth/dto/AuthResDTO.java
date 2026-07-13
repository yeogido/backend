package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class AuthResDTO {

  @Schema(name = "AuthSignUpRes", description = "이메일 회원가입 응답")
  public record SignUp(
    @Schema(description = "사용자 ID", example = "1")
    Long userId
  ) {}

  @Schema(name = "AuthTokenRes", description = "토큰 응답")
  public record Token(
    @Schema(description = "사용자 ID", example = "1")
    Long userId,

    @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWI...")
    String accessToken,

    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.xyz...")
    String refreshToken
  ) {}

  @Schema(name = "AuthEmailCheckRes", description = "이메일 중복 확인 응답")
  public record EmailCheck(
    @Schema(description = "이메일 사용 가능 여부", example = "true")
    boolean isAvailable
  ) {}

  @Schema(name = "AuthPasswordVerifyCodeRes", description = "비밀번호 찾기 인증번호 검증 응답")
  public record PasswordVerifyCode(
    @Schema(description = "비밀번호 재설정 임시 토큰", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String resetToken
  ) {}
}

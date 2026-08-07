package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.user.enums.UserRole;
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

    @Schema(description = "사용자 역할. 관리자 화면 분기는 ADMIN 여부로 판단합니다.", example = "USER", allowableValues = {"USER", "ADMIN", "BUSINESS"})
    UserRole role,

    @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWI...")
    String accessToken,

    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.xyz...")
    String refreshToken
  ) {}

  @Schema(name = "AuthSocialLoginRes", description = "소셜 로그인 응답")
  public record SocialLogin(
    @Schema(description = "신규 소셜 사용자 여부", example = "true")
    boolean isNewUser,

    @Schema(description = "사용자 ID. 기존 사용자 로그인 성공 시 반환", example = "2")
    Long userId,

    @Schema(description = "사용자 역할. 기존 사용자 로그인 성공 시 반환되며 관리자 화면 분기는 ADMIN 여부로 판단합니다.", example = "USER", allowableValues = {"USER", "ADMIN", "BUSINESS"})
    UserRole role,

    @Schema(description = "액세스 토큰. 기존 사용자 로그인 성공 시 반환", example = "eyJhbGciOiJIUzI1NiJ9...")
    String accessToken,

    @Schema(description = "리프레시 토큰. 기존 사용자 로그인 성공 시 반환", example = "eyJhbGciOiJIUzI1NiJ9...")
    String refreshToken,

    @Schema(description = "프로필 작성용 임시 토큰. 신규 소셜 사용자일 때 반환", example = "temp_social_signup_token")
    String temporaryToken,

    @Schema(description = "소셜 Provider에서 조회한 이메일. 신규 소셜 사용자일 때 반환", example = "kakao_user@example.com")
    String email,

    @Schema(description = "소셜 Provider에서 조회한 이름. 신규 소셜 사용자일 때 반환", example = "홍길동")
    String name
  ) {}

  @Schema(name = "AuthEmailCheckRes", description = "이메일 중복 확인 응답")
  public record EmailCheck(
    @Schema(description = "이메일 사용 가능 여부", example = "true")
    boolean isAvailable
  ) {}

  @Schema(name = "AuthEmailVerifyCodeRes", description = "회원가입 이메일 인증번호 검증 응답")
  public record EmailVerifyCode(
    @Schema(description = "회원가입 이메일 인증 토큰", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String emailVerificationToken
  ) {}

  @Schema(name = "AuthPasswordVerifyCodeRes", description = "비밀번호 찾기 인증번호 검증 응답")
  public record PasswordVerifyCode(
    @Schema(description = "비밀번호 재설정 임시 토큰", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String resetToken
  ) {}
}

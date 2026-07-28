package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.auth.enums.SocialProvider;
import com.yeogido.backend.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class AuthReqDTO {

  @Schema(name = "AuthSignUpReq", description = "이메일 회원가입 요청")
  public record SignUp(
    @Schema(description = "이메일", example = "abc@example.com")
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email,

    @Schema(description = "비밀번호", example = "password123!")
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(
      regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
      message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자여야 합니다."
    )
    String password,

    @Schema(description = "닉네임", example = "길동이")
    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    String nickname,

    @Schema(description = "성별", example = "MALE", allowableValues = {"MALE", "FEMALE"})
    @NotNull(message = "성별은 필수 입력값입니다.")
    Gender gender,

    @Schema(description = "출생연도", example = "2001")
    @NotBlank(message = "출생연도는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}$", message = "출생연도는 4자리 숫자여야 합니다.")
    String birthYear,

    @Schema(description = "지역 ID", example = "1")
    @NotNull(message = "지역 ID는 필수 입력값입니다.")
    @Positive(message = "지역 ID는 양수여야 합니다.")
    Long regionId
  ) {}

  @Schema(name = "AuthLoginReq", description = "이메일 로그인 요청")
  public record Login(
    @Schema(description = "이메일", example = "abc@example.com")
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email,

    @Schema(description = "비밀번호", example = "password123!")
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    String password
  ) {}

  @Schema(name = "AuthSocialLoginReq", description = "소셜 로그인 요청")
  public record SocialLogin(
    @Schema(description = "소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER"})
    @NotNull(message = "소셜 로그인 제공자는 필수 입력값입니다.")
    SocialProvider provider,

    @Schema(description = "소셜 accessToken", example = "kakao_access_token")
    @NotBlank(message = "소셜 accessToken은 필수 입력값입니다.")
    String accessToken
  ) {}

  @Schema(name = "AuthSocialSignupCompleteReq", description = "소셜 로그인 추가 프로필 작성 완료 요청")
  public record SocialSignupComplete(
    @Schema(description = "소셜 회원가입 임시 토큰", example = "temp_social_signup_token")
    @NotBlank(message = "소셜 회원가입 임시 토큰은 필수 입력값입니다.")
    String temporaryToken,

    @Schema(description = "사용자 이름. 소셜 계정 이름을 기본값으로 사용하며 프로필에서 수정 가능합니다.", example = "홍길동")
    @NotBlank(message = "이름은 필수 입력값입니다.")
    String name,

    @Schema(description = "성별", example = "MALE")
    @NotNull(message = "성별은 필수 입력값입니다.")
    Gender gender,

    @Schema(description = "출생연도", example = "2001")
    @NotBlank(message = "출생연도는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}$", message = "출생연도는 4자리 숫자여야 합니다.")
    String birthYear,

    @Schema(description = "지역 ID", example = "1")
    @NotNull(message = "지역 ID는 필수 입력값입니다.")
    Long regionId
  ) {}

  @Schema(name = "AuthReissueReq", description = "토큰 재발급 요청")
  public record Reissue(
    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.xyz...")
    @NotBlank(message = "리프레시 토큰은 필수 입력값입니다.")
    String refreshToken
  ) {}

  @Schema(name = "AuthPasswordSendCodeReq", description = "비밀번호 찾기 인증번호 발송 요청")
  public record PasswordSendCode(
    @Schema(description = "이메일", example = "abc@example.com")
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email
  ) {}

  @Schema(name = "AuthPasswordVerifyCodeReq", description = "비밀번호 찾기 인증번호 검증 요청")
  public record PasswordVerifyCode(
    @Schema(description = "이메일", example = "abc@example.com")
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email,

    @Schema(description = "인증번호", example = "123456")
    @NotBlank(message = "인증번호는 필수 입력값입니다.")
    String authCode
  ) {}
}

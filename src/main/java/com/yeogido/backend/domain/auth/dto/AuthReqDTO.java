package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.auth.enums.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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

    @Schema(description = "이름", example = "홍길동")
    @NotBlank(message = "이름은 필수 입력값입니다.")
    String name,

    @Schema(description = "닉네임", example = "길동이")
    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    String nickname,

    @Schema(description = "지역", example = "서울")
    @NotBlank(message = "지역은 필수 입력값입니다.")
    String region
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
    @Schema(description = "소셜 로그인 제공자", example = "KAKAO")
    @NotNull(message = "소셜 로그인 제공자는 필수 입력값입니다.")
    SocialProvider provider,

    @Schema(description = "소셜 제공자 사용자 식별자", example = "1234567890")
    @NotBlank(message = "소셜 제공자 사용자 식별자는 필수 입력값입니다.")
    String providerId,

    @Schema(description = "소셜 계정 이메일", example = "kakao_user@example.com")
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email
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

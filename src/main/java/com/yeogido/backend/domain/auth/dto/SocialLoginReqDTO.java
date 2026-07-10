package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.auth.enums.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "SocialLoginReqDTO", description = "소셜 로그인 요청")
public record SocialLoginReqDTO(
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

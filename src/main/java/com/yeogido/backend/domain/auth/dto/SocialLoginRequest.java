package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.auth.enums.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequest(
  @NotNull(message = "소셜 로그인 제공자는 필수 입력값입니다.")
  SocialProvider provider,

  @NotBlank(message = "액세스 토큰은 필수 입력값입니다.")
  String accessToken
) {}

package com.yeogido.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
  @NotBlank(message = "리프레시 토큰은 필수 입력값입니다.")
  String refreshToken
) {}

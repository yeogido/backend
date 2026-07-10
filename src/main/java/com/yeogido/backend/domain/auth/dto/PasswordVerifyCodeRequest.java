package com.yeogido.backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordVerifyCodeRequest(
  @NotBlank(message = "이메일은 필수 입력값입니다.")
  @Email(message = "올바른 이메일 형식이어야 합니다.")
  String email,

  @NotBlank(message = "인증번호는 필수 입력값입니다.")
  String code
) {}

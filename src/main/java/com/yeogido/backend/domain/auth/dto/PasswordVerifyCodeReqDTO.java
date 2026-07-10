package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "PasswordVerifyCodeReqDTO", description = "비밀번호 찾기 인증번호 검증 요청")
public record PasswordVerifyCodeReqDTO(
  @Schema(description = "이메일", example = "abc@example.com")
  @NotBlank(message = "이메일은 필수 입력값입니다.")
  @Email(message = "올바른 이메일 형식이어야 합니다.")
  String email,

  @Schema(description = "인증번호", example = "123456")
  @NotBlank(message = "인증번호는 필수 입력값입니다.")
  String authCode
) {}

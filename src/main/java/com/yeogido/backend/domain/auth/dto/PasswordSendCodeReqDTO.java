package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "PasswordSendCodeReqDTO", description = "비밀번호 찾기 인증번호 발송 요청")
public record PasswordSendCodeReqDTO(
  @Schema(description = "이메일", example = "abc@example.com")
  @NotBlank(message = "이메일은 필수 입력값입니다.")
  @Email(message = "올바른 이메일 형식이어야 합니다.")
  String email
) {}

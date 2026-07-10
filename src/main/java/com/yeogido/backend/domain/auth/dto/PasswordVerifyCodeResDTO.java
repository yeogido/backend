package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PasswordVerifyCodeResDTO", description = "비밀번호 찾기 인증번호 검증 응답")
public record PasswordVerifyCodeResDTO(
  @Schema(description = "비밀번호 재설정 임시 토큰", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
  String resetToken
) {}

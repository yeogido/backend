package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SignUpResDTO", description = "이메일 회원가입 응답")
public record SignUpResDTO(
  @Schema(description = "사용자 ID", example = "1")
  Long userId
) {}

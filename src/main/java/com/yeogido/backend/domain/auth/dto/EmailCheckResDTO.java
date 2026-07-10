package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailCheckResDTO", description = "이메일 중복 확인 응답")
public record EmailCheckResDTO(
  @Schema(description = "이메일 사용 가능 여부", example = "true")
  boolean isAvailable
) {}

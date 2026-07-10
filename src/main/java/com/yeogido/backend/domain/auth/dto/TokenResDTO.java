package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResDTO", description = "토큰 응답")
public record TokenResDTO(
  @Schema(description = "사용자 ID", example = "1")
  Long userId,

  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWI...")
  String accessToken,

  @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.xyz...")
  String refreshToken
) {}

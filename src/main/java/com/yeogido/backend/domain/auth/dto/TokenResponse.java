package com.yeogido.backend.domain.auth.dto;

public record TokenResponse(
  String accessToken,
  String refreshToken,
  Long userId,
  String nickname
) {}

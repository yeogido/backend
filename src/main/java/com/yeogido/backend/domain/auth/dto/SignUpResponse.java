package com.yeogido.backend.domain.auth.dto;

public record SignUpResponse(
  Long userId,
  String email,
  String nickname
) {}

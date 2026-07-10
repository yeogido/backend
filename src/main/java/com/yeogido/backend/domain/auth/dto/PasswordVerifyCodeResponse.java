package com.yeogido.backend.domain.auth.dto;

public record PasswordVerifyCodeResponse(
  String verificationToken
) {}

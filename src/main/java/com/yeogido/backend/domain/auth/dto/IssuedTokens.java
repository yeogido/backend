package com.yeogido.backend.domain.auth.dto;

/** JWT 발급 결과. 사용자 상태와 무관하게 토큰 값만 전달합니다. */
public record IssuedTokens(String accessToken, String refreshToken) {}

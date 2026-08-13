package com.yeogido.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoTokenResponse(
  @JsonProperty("access_token")
  String accessToken
) {
}

package com.yeogido.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.enums.SocialProvider;

public record NaverUserInfoResponse(
  String resultcode,
  String message,
  Response response
) {

  public SocialUserInfo toSocialUserInfo() {
    return new SocialUserInfo(
      SocialProvider.NAVER,
      response.id(),
      response.email(),
      response.name(),
      response.profileImageUrl()
    );
  }

  public record Response(
    String id,
    String email,
    String name,
    @JsonProperty("profile_image")
    String profileImageUrl
  ) {
  }
}

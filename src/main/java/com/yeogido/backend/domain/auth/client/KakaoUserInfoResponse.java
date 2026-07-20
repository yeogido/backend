package com.yeogido.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.enums.SocialProvider;

public record KakaoUserInfoResponse(
  Long id,
  @JsonProperty("kakao_account")
  KakaoAccount kakaoAccount,
  Properties properties
) {

  public SocialUserInfo toSocialUserInfo() {
    String email = kakaoAccount == null ? null : kakaoAccount.email();
    String name = kakaoAccount == null ? null : kakaoAccount.name();
    if (name == null && properties != null) {
      name = properties.nickname();
    }
    String profileImageUrl = properties == null ? null : properties.profileImage();

    return new SocialUserInfo(
      SocialProvider.KAKAO,
      String.valueOf(id),
      email,
      name,
      profileImageUrl
    );
  }

  public record KakaoAccount(
    String email,
    String name
  ) {
  }

  public record Properties(
    String nickname,
    @JsonProperty("profile_image")
    String profileImage
  ) {
  }
}

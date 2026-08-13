package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.auth.enums.SocialProvider;

public record SocialSignupTokenPayload(
  SocialProvider provider,
  String providerId,
  String email,
  String name,
  String profileImageUrl
) {

  public static SocialSignupTokenPayload from(SocialUserInfo socialUserInfo) {
    return new SocialSignupTokenPayload(
      socialUserInfo.provider(),
      socialUserInfo.providerId(),
      socialUserInfo.email(),
      socialUserInfo.name(),
      socialUserInfo.profileImageUrl()
    );
  }
}

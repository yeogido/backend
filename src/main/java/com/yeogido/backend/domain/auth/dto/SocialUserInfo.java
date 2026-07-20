package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.auth.enums.SocialProvider;

public record SocialUserInfo(
  SocialProvider provider,
  String providerId,
  String email,
  String name,
  String profileImageUrl
) {
}

package com.yeogido.backend.domain.auth.converter;

import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;

public class AuthConverter {

  private AuthConverter() {
  }

  public static AuthResDTO.SocialLogin toExistingSocialLoginResponse(AuthResDTO.Token token) {
    return new AuthResDTO.SocialLogin(
      false,
      token.userId(),
      token.accessToken(),
      token.refreshToken(),
      null,
      null,
      null
    );
  }

  public static AuthResDTO.SocialLogin toNewSocialLoginResponse(
    String temporaryToken,
    SocialUserInfo socialUserInfo
  ) {
    return new AuthResDTO.SocialLogin(
      true,
      null,
      null,
      null,
      temporaryToken,
      socialUserInfo.email(),
      socialUserInfo.name()
    );
  }
}

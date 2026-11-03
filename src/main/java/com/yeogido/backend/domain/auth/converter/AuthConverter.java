package com.yeogido.backend.domain.auth.converter;

import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.dto.IssuedTokens;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.user.entity.User;

public class AuthConverter {

  private AuthConverter() {
  }

  public static AuthResDTO.Token toTokenResponse(User user, IssuedTokens tokens, boolean termsAgreementRequired) {
    return new AuthResDTO.Token(user.getId(), user.getRole(), tokens.accessToken(), tokens.refreshToken(),
      termsAgreementRequired);
  }

  public static AuthResDTO.SocialLogin toExistingSocialLoginResponse(AuthResDTO.Token token) {
    return new AuthResDTO.SocialLogin(
      false,
      token.userId(),
      token.role(),
      token.accessToken(),
      token.refreshToken(),
      null,
      null,
      null,
      token.termsAgreementRequired()
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
      null,
      temporaryToken,
      socialUserInfo.email(),
      socialUserInfo.name(),
      true
    );
  }
}

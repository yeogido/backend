package com.yeogido.backend.domain.auth.client;

import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoUserInfoClient {

  private final RestClient.Builder restClientBuilder;

  @Value("${app.oauth.kakao.user-info-uri}")
  private String userInfoUri;

  public SocialUserInfo getUserInfo(String accessToken) {
    try {
      KakaoUserInfoResponse response = restClientBuilder.build()
        .get()
        .uri(userInfoUri)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .body(KakaoUserInfoResponse.class);

      if (response == null || response.id() == null) {
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN);
      }

      return response.toSocialUserInfo();
    } catch (RestClientException e) {
      throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN);
    }
  }
}

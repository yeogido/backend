package com.yeogido.backend.domain.auth.client;

import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoTokenClient {

  private final RestClient.Builder restClientBuilder;

  @Value("${app.oauth.kakao.token-uri}")
  private String tokenUri;

  @Value("${app.oauth.kakao.client-id}")
  private String clientId;

  @Value("${app.oauth.kakao.client-secret:}")
  private String clientSecret;

  public String getAccessToken(String authorizationCode, String redirectUri) {
    if (!StringUtils.hasText(clientId)) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }

    try {
      KakaoTokenResponse response = restClientBuilder.build()
        .post()
        .uri(tokenUri)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(createTokenRequestBody(authorizationCode, redirectUri))
        .retrieve()
        .body(KakaoTokenResponse.class);

      if (response == null || !StringUtils.hasText(response.accessToken())) {
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE);
      }

      return response.accessToken();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE);
      }

      log.warn("Kakao token API returned server error. status={}", e.getStatusCode(), e);
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    } catch (RestClientException e) {
      log.warn("Kakao token API request failed.", e);
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private MultiValueMap<String, String> createTokenRequestBody(String authorizationCode, String redirectUri) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("client_id", clientId);
    body.add("redirect_uri", redirectUri);
    body.add("code", authorizationCode);

    if (StringUtils.hasText(clientSecret)) {
      body.add("client_secret", clientSecret);
    }

    return body;
  }
}

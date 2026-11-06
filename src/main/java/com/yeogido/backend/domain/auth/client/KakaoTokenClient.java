package com.yeogido.backend.domain.auth.client;

import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
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
        logFailure("invalid_response", null, AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE.getCode());
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE);
      }

      return response.accessToken();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        logFailure(SafeLogDetails.httpFailureType(e.getStatusCode().value()), e.getStatusCode().value(), AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE.getCode());
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE);
      }

      logFailure(SafeLogDetails.httpFailureType(e.getStatusCode().value()), e.getStatusCode().value(), GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode());
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    } catch (RestClientException e) {
      logFailure(SafeLogDetails.failureType(e), null, GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode());
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private void logFailure(String failureType, Integer status, String errorCode) {
    var event = log.atWarn()
        .addKeyValue("event", "external_api.call.failed")
        .addKeyValue("provider", "kakao")
        .addKeyValue("operation", "token")
        .addKeyValue("failureType", failureType)
        .addKeyValue("errorCode", errorCode)
        .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, status));
    if (status != null) {
      event.addKeyValue("upstreamHttpStatus", status);
    }
    event.log("External API call failed.");
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

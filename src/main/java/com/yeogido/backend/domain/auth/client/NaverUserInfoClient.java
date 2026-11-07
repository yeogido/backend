package com.yeogido.backend.domain.auth.client;

import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class NaverUserInfoClient {

  private final RestClient.Builder restClientBuilder;

  @Value("${app.oauth.naver.user-info-uri}")
  private String userInfoUri;

  public SocialUserInfo getUserInfo(String accessToken) {
    try {
      NaverUserInfoResponse response = restClientBuilder.build()
        .get()
        .uri(userInfoUri)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .body(NaverUserInfoResponse.class);

      if (response == null || response.response() == null || response.response().id() == null) {
        logFailure("invalid_response", null);
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN);
      }

      return response.toSocialUserInfo();
    } catch (RestClientException e) {
      Integer status = e instanceof RestClientResponseException responseFailure
          ? responseFailure.getStatusCode().value() : null;
      logFailure(status == null ? SafeLogDetails.failureType(e) : SafeLogDetails.httpFailureType(status), status);
      throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN);
    }
  }

  private void logFailure(String failureType, Integer status) {
    var event = log.atWarn()
        .addKeyValue("event", "external_api.call.failed")
        .addKeyValue("provider", "naver")
        .addKeyValue("operation", "user_info")
        .addKeyValue("failureType", failureType)
        .addKeyValue("errorCode", AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN.getCode())
        .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, status));
    if (status != null) {
      event.addKeyValue("upstreamHttpStatus", status);
    }
    event.log("External API call failed.");
  }
}

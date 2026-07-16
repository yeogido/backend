package com.yeogido.backend.domain.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.dto.SocialSignupTokenPayload;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialSignupTokenService {

  private final ObjectMapper objectMapper;

  @Value("${app.oauth.social-signup-token-secret}")
  private String tokenSecret;

  @Value("${app.oauth.social-signup-token-expiration-minutes}")
  private long expirationMinutes;

  public String issueToken(SocialUserInfo socialUserInfo) {
    SocialSignupToken token = new SocialSignupToken(
      SocialSignupTokenPayload.from(socialUserInfo),
      Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES).toEpochMilli()
    );

    try {
      String payload = encode(objectMapper.writeValueAsString(token));
      String signature = sign(payload);
      return payload + "." + signature;
    } catch (JsonProcessingException e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  public SocialSignupTokenPayload getPayload(String temporaryToken) {
    try {
      String[] tokenParts = temporaryToken.split("\\.");

      if (tokenParts.length != 2 || !sign(tokenParts[0]).equals(tokenParts[1])) {
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_SIGNUP_TOKEN);
      }

      SocialSignupToken token = objectMapper.readValue(
        decode(tokenParts[0]),
        SocialSignupToken.class
      );

      if (token.expiresAt() < Instant.now().toEpochMilli()) {
        throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_SIGNUP_TOKEN);
      }

      return token.payload();
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_SIGNUP_TOKEN);
    }
  }

  public void deleteToken(String temporaryToken) {
  }

  private String encode(String value) {
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private record SocialSignupToken(
    SocialSignupTokenPayload payload,
    long expiresAt
  ) {
  }
}

package com.yeogido.backend.domain.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.dto.SocialSignupTokenPayload;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialSignupTokenService {

  private static final String KEY_PREFIX = "auth:social-signup:";

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${app.oauth.social-signup-token-expiration-minutes}")
  private long expirationMinutes;

  public String issueToken(SocialUserInfo socialUserInfo) {
    String temporaryToken = UUID.randomUUID().toString();
    SocialSignupTokenPayload payload = SocialSignupTokenPayload.from(socialUserInfo);

    try {
      String payloadJson = objectMapper.writeValueAsString(payload);
      stringRedisTemplate.opsForValue().set(
        KEY_PREFIX + temporaryToken,
        payloadJson,
        Duration.ofMinutes(expirationMinutes)
      );
      return temporaryToken;
    } catch (JsonProcessingException e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  public SocialSignupTokenPayload getPayload(String temporaryToken) {
    String payloadJson = stringRedisTemplate.opsForValue().get(KEY_PREFIX + temporaryToken);

    if (payloadJson == null) {
      throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_SIGNUP_TOKEN);
    }

    try {
      return objectMapper.readValue(payloadJson, SocialSignupTokenPayload.class);
    } catch (JsonProcessingException e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  public void deleteToken(String temporaryToken) {
    stringRedisTemplate.delete(KEY_PREFIX + temporaryToken);
  }
}

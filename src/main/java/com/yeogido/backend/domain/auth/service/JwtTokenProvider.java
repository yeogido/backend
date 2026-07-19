package com.yeogido.backend.domain.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

  private static final String TOKEN_TYPE_ACCESS = "ACCESS";
  private static final String TOKEN_TYPE_REFRESH = "REFRESH";

  private final ObjectMapper objectMapper;

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.access-token-expiration-minutes}")
  private long accessTokenExpirationMinutes;

  @Value("${app.jwt.refresh-token-expiration-days}")
  private long refreshTokenExpirationDays;

  public AuthResDTO.Token issueToken(User user) {
    return new AuthResDTO.Token(
      user.getId(),
      createToken(user, TOKEN_TYPE_ACCESS, Instant.now().plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES)),
      createToken(user, TOKEN_TYPE_REFRESH, Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS))
    );
  }

  private String createToken(User user, String tokenType, Instant expiresAt) {
    try {
      String header = encode(objectMapper.writeValueAsString(Map.of(
        "alg", "HS256",
        "typ", "JWT"
      )));
      String payload = encode(objectMapper.writeValueAsString(Map.of(
        "sub", String.valueOf(user.getId()),
        "userId", user.getId(),
        "role", user.getRole().name(),
        "type", tokenType,
        "iat", Instant.now().getEpochSecond(),
        "exp", expiresAt.getEpochSecond()
      )));
      String unsignedToken = header + "." + payload;
      return unsignedToken + "." + sign(unsignedToken);
    } catch (JsonProcessingException e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private String encode(String value) {
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}

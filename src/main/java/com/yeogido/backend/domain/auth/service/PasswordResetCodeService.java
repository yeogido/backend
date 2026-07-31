package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.redis.RedisKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetCodeService {

  private static final String DOMAIN = "auth";
  private static final String PASSWORD_RESET_CODE = "password-reset-code";
  private static final String PASSWORD_RESET_TOKEN = "password-reset-token";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StringRedisTemplate stringRedisTemplate;

  @Value("${app.password-reset.code-expiration-minutes}")
  private long codeExpirationMinutes;

  @Value("${app.password-reset.token-expiration-minutes}")
  private long tokenExpirationMinutes;

  public String issueCode(String email) {
    String authCode = createAuthCode();

    stringRedisTemplate.opsForValue().set(
      codeKey(email),
      authCode,
      Duration.ofMinutes(codeExpirationMinutes)
    );

    return authCode;
  }

  public String verifyCodeAndIssueResetToken(String email, String authCode) {
    String savedAuthCode = stringRedisTemplate.opsForValue().get(codeKey(email));

    if (!authCode.equals(savedAuthCode)) {
      throw new GeneralException(AuthErrorCode.INVALID_PASSWORD_RESET_CODE);
    }

    stringRedisTemplate.delete(codeKey(email));

    String resetToken = UUID.randomUUID().toString();
    stringRedisTemplate.opsForValue().set(
      tokenKey(resetToken),
      email,
      Duration.ofMinutes(tokenExpirationMinutes)
    );

    return resetToken;
  }

  private String createAuthCode() {
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }

  private String codeKey(String email) {
    return RedisKey.of(DOMAIN, PASSWORD_RESET_CODE, email);
  }

  private String tokenKey(String resetToken) {
    return RedisKey.of(DOMAIN, PASSWORD_RESET_TOKEN, resetToken);
  }
}

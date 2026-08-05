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
public class EmailVerificationService {

  private static final String DOMAIN = "auth";
  private static final String EMAIL_VERIFICATION_CODE = "email-verification-code";
  private static final String EMAIL_VERIFICATION_TOKEN = "email-verification-token";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StringRedisTemplate stringRedisTemplate;

  @Value("${app.email-verification.code-expiration-minutes}")
  private long codeExpirationMinutes;

  @Value("${app.email-verification.token-expiration-minutes}")
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

  public String verifyCodeAndIssueToken(String email, String authCode) {
    String savedAuthCode = stringRedisTemplate.opsForValue().get(codeKey(email));

    if (!authCode.equals(savedAuthCode)) {
      throw new GeneralException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE);
    }

    stringRedisTemplate.delete(codeKey(email));

    String emailVerificationToken = UUID.randomUUID().toString();
    stringRedisTemplate.opsForValue().set(
      tokenKey(emailVerificationToken),
      email,
      Duration.ofMinutes(tokenExpirationMinutes)
    );

    return emailVerificationToken;
  }

  public void verifyToken(String email, String emailVerificationToken) {
    String savedEmail = stringRedisTemplate.opsForValue().get(tokenKey(emailVerificationToken));

    if (!email.equals(savedEmail)) {
      throw new GeneralException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
    }
  }

  public void deleteToken(String emailVerificationToken) {
    stringRedisTemplate.delete(tokenKey(emailVerificationToken));
  }

  private String createAuthCode() {
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }

  private String codeKey(String email) {
    return RedisKey.of(DOMAIN, EMAIL_VERIFICATION_CODE, email);
  }

  private String tokenKey(String emailVerificationToken) {
    return RedisKey.of(DOMAIN, EMAIL_VERIFICATION_TOKEN, emailVerificationToken);
  }
}

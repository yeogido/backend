package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.global.redis.RedisKey;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String DOMAIN = "auth";
  private static final String REFRESH_TOKEN = "refresh-token";

  private final StringRedisTemplate stringRedisTemplate;

  @Value("${app.jwt.refresh-token-expiration-days}")
  private long refreshTokenExpirationDays;

  public void save(Long userId, String refreshToken) {
    stringRedisTemplate.opsForValue().set(
      key(userId),
      refreshToken,
      Duration.ofDays(refreshTokenExpirationDays)
    );
  }

  public void delete(Long userId) {
    stringRedisTemplate.delete(key(userId));
  }

  public boolean matches(Long userId, String refreshToken) {
    String savedRefreshToken = stringRedisTemplate.opsForValue().get(key(userId));
    return refreshToken.equals(savedRefreshToken);
  }

  private String key(Long userId) {
    return RedisKey.of(DOMAIN, REFRESH_TOKEN, userId.toString());
  }
}

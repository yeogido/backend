package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.global.redis.RedisKey;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String DOMAIN = "auth";
  private static final String REFRESH_TOKEN = "refresh-token";
  private static final Long ROTATE_SUCCESS = 1L;
  private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
    local current = redis.call('GET', KEYS[1])
    if current == ARGV[1] then
      redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
      return 1
    end
    return 0
    """, Long.class);

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

  public boolean rotate(Long userId, String oldRefreshToken, String newRefreshToken) {
    Long result = stringRedisTemplate.execute(
      ROTATE_SCRIPT,
      List.of(key(userId)),
      oldRefreshToken,
      newRefreshToken,
      String.valueOf(Duration.ofDays(refreshTokenExpirationDays).toMillis())
    );

    return ROTATE_SUCCESS.equals(result);
  }

  private String key(Long userId) {
    return RedisKey.of(DOMAIN, REFRESH_TOKEN, userId.toString());
  }
}

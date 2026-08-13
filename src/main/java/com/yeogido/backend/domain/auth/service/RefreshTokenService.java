package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.global.redis.RedisKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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

  public void save(Long userId, String sessionId, String refreshToken) {
    stringRedisTemplate.opsForValue().set(
      key(userId, sessionId),
      refreshToken,
      Duration.ofDays(refreshTokenExpirationDays)
    );
  }

  public void delete(Long userId, String sessionId) {
    stringRedisTemplate.delete(key(userId, sessionId));
  }

  public void delete(Long userId) {
    Set<String> keys = scanKeys(keyPattern(userId));
    if (!keys.isEmpty()) {
      stringRedisTemplate.delete(keys);
    }
  }

  public boolean rotate(Long userId, String sessionId, String oldRefreshToken, String newRefreshToken) {
    Long result = stringRedisTemplate.execute(
      ROTATE_SCRIPT,
      List.of(key(userId, sessionId)),
      oldRefreshToken,
      newRefreshToken,
      String.valueOf(Duration.ofDays(refreshTokenExpirationDays).toMillis())
    );

    return ROTATE_SUCCESS.equals(result);
  }

  private String key(Long userId, String sessionId) {
    return RedisKey.of(DOMAIN, REFRESH_TOKEN, userId.toString(), sessionId);
  }

  private String keyPattern(Long userId) {
    return RedisKey.of(DOMAIN, REFRESH_TOKEN, userId.toString(), "*");
  }

  private Set<String> scanKeys(String pattern) {
    return stringRedisTemplate.execute((RedisConnection connection) -> {
      Set<String> keys = new HashSet<>();
      ScanOptions options = ScanOptions.scanOptions()
        .match(pattern)
        .count(1000)
        .build();

      try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
        while (cursor.hasNext()) {
          keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
        }
      }

      return keys;
    });
  }
}

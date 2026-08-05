package com.yeogido.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final Long USER_ID = 1L;
  private static final String SESSION_ID = "session-id";
  private static final String KEY = "auth:refresh-token:1:session-id";
  private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
  private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
  private static final String TTL_MILLISECONDS = "604800000";

  @Mock
  private StringRedisTemplate stringRedisTemplate;

  private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    refreshTokenService = new RefreshTokenService(stringRedisTemplate);
    ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationDays", 7L);
  }

  @Test
  @DisplayName("Refresh Token이 Redis 저장값과 일치하면 원자적으로 새 토큰으로 교체한다")
  void rotateSuccess() {
    when(stringRedisTemplate.execute(
      anyRefreshTokenRotateScript(),
      eq(List.of(KEY)),
      eq(OLD_REFRESH_TOKEN),
      eq(NEW_REFRESH_TOKEN),
      eq(TTL_MILLISECONDS)
    )).thenReturn(1L);

    boolean result = refreshTokenService.rotate(USER_ID, SESSION_ID, OLD_REFRESH_TOKEN, NEW_REFRESH_TOKEN);

    assertThat(result).isTrue();
    verify(stringRedisTemplate).execute(
      anyRefreshTokenRotateScript(),
      eq(List.of(KEY)),
      eq(OLD_REFRESH_TOKEN),
      eq(NEW_REFRESH_TOKEN),
      eq(TTL_MILLISECONDS)
    );
  }

  @Test
  @DisplayName("Refresh Token이 Redis 저장값과 일치하지 않으면 교체하지 않는다")
  void rotateFail() {
    when(stringRedisTemplate.execute(
      anyRefreshTokenRotateScript(),
      eq(List.of(KEY)),
      eq(OLD_REFRESH_TOKEN),
      eq(NEW_REFRESH_TOKEN),
      eq(TTL_MILLISECONDS)
    )).thenReturn(0L);

    boolean result = refreshTokenService.rotate(USER_ID, SESSION_ID, OLD_REFRESH_TOKEN, NEW_REFRESH_TOKEN);

    assertThat(result).isFalse();
  }

  private DefaultRedisScript<Long> anyRefreshTokenRotateScript() {
    return any();
  }
}

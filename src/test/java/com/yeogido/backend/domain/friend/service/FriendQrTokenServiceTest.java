package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendQrTokenServiceTest {

    private static final Duration TTL = Duration.ofSeconds(180);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FriendQrTokenService friendQrTokenService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void issueStoresTokenForThreeMinutes() {
        LocalDateTime before = LocalDateTime.now();

        FriendQrTokenService.IssuedToken issued = friendQrTokenService.issue(1L);

        verify(valueOperations).set("friend:qr:token:" + issued.token(), "1", TTL);
        verify(valueOperations).getAndSet("friend:qr:user:1", issued.token());
        verify(stringRedisTemplate).expire("friend:qr:user:1", TTL);
        verify(stringRedisTemplate, never()).delete(anyString());
        assertThat(issued.expiresAt()).isBetween(before.plus(TTL), LocalDateTime.now().plus(TTL));
    }

    @Test
    void issueDeletesPreviousToken() {
        when(valueOperations.getAndSet(eq("friend:qr:user:1"), anyString())).thenReturn("previous");

        friendQrTokenService.issue(1L);

        verify(stringRedisTemplate).delete("friend:qr:token:previous");
    }

    @Test
    void issueCreatesDifferentTokenEachTime() {
        assertThat(friendQrTokenService.issue(1L).token()).isNotEqualTo(friendQrTokenService.issue(1L).token());
    }

    @Test
    void findUserIdReturnsOwnerOrEmptyWhenExpired() {
        when(valueOperations.get("friend:qr:token:valid")).thenReturn("1");
        when(valueOperations.get("friend:qr:token:expired")).thenReturn(null);

        assertThat(friendQrTokenService.findUserId("valid")).contains(1L);
        assertThat(friendQrTokenService.findUserId("expired")).isEmpty();
    }
}

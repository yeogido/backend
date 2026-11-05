package com.yeogido.backend.domain.friend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FriendQrTokenService {

    private static final String TOKEN_KEY_PREFIX = "friend:qr:token:";
    private static final String USER_KEY_PREFIX = "friend:qr:user:";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(180);

    private final StringRedisTemplate stringRedisTemplate;

    // 사용자당 유효한 토큰은 하나만 유지. 동시에 발급해도 먼저 저장된 토큰은 지워짐
    public IssuedToken issue(Long userId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(TOKEN_TTL);

        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, userId.toString(), TOKEN_TTL);

        String userKey = USER_KEY_PREFIX + userId;
        String previousToken = stringRedisTemplate.opsForValue().getAndSet(userKey, token);
        stringRedisTemplate.expire(userKey, TOKEN_TTL);
        if (previousToken != null) {
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + previousToken);
        }

        return new IssuedToken(token, expiresAt);
    }

    public Optional<Long> findUserId(String token) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token))
                .map(Long::valueOf);
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }
}

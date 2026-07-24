package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.global.redis.RedisKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private CourseRedisRepository courseRedisRepository;

    @Test
    void increaseViewCountUsesActivityZSetAndTtl() {
        LocalDate date = LocalDate.of(2026, 7, 23);
        String key = RedisKey.dated("course", "activity", date, "views");

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore(key, "42", 1))
                .thenReturn(1.0);

        courseRedisRepository.increaseViewCount(42L, date);

        verify(zSetOperations).incrementScore(key, "42", 1);
        verify(stringRedisTemplate).expire(key, Duration.ofDays(8));
    }

    @Test
    void increaseLikeCountUsesActivityZSetAndTtl() {
        LocalDate today = LocalDate.now();
        String key = RedisKey.dated("course", "activity", today, "likes");

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore(key, "42", 1))
                .thenReturn(1.0);

        courseRedisRepository.increaseLikeCount(42L);

        verify(zSetOperations).incrementScore(key, "42", 1);
        verify(stringRedisTemplate).expire(key, Duration.ofDays(8));
    }

    @Test
    void decreaseLikeCountUsesActivityZSetAndTtl() {
        LocalDate today = LocalDate.now();
        String key = RedisKey.dated("course", "activity", today, "likes");

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore(key, "42", -1))
                .thenReturn(0.0);

        courseRedisRepository.decreaseLikeCount(42L);

        verify(zSetOperations).incrementScore(key, "42", -1);
        verify(stringRedisTemplate).expire(key, Duration.ofDays(8));
    }

    @Test
    void saveCreatedEventUsesActivityZSetAndTtl() {
        LocalDate date = LocalDate.of(2026, 7, 23);
        String key = RedisKey.dated("course", "activity", date, "creates");

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore(key, "42", 1))
                .thenReturn(1.0);

        courseRedisRepository.saveCreatedEvent(42L, date);

        verify(zSetOperations).incrementScore(key, "42", 1);
        verify(stringRedisTemplate).expire(key, Duration.ofDays(8));
    }
}
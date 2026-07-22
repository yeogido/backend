package com.yeogido.backend.domain.course.repository;

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
    void increaseDailyViewCountUsesActivityZSetAndTtl() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore("course:activity:20260723:views", "42", 1))
                .thenReturn(1.0);

        courseRedisRepository.increaseDailyViewCount(42L, date);

        verify(zSetOperations).incrementScore("course:activity:20260723:views", "42", 1);
        verify(stringRedisTemplate).expire("course:activity:20260723:views", Duration.ofDays(8));
    }

    @Test
    void increaseDailyLikeCountUsesActivityZSetAndTtl() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore("course:activity:20260723:likes", "42", 1))
                .thenReturn(1.0);

        courseRedisRepository.increaseDailyLikeCount(42L, date);

        verify(zSetOperations).incrementScore("course:activity:20260723:likes", "42", 1);
        verify(stringRedisTemplate).expire("course:activity:20260723:likes", Duration.ofDays(8));
    }

    @Test
    void saveCreatedEventUsesActivityZSetAndTtl() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.incrementScore("course:activity:20260723:creates", "42", 1))
                .thenReturn(1.0);

        courseRedisRepository.saveCreatedEvent(42L, date);

        verify(zSetOperations).incrementScore("course:activity:20260723:creates", "42", 1);
        verify(stringRedisTemplate).expire("course:activity:20260723:creates", Duration.ofDays(8));
    }
}

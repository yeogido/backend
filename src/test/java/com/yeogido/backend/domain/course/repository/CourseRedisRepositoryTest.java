package com.yeogido.backend.domain.course.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CourseRedisRepository courseRedisRepository;

    @Test
    void saveCreatedEventUsesDailyEventSetAndCreatedAtValue() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-23T10:15:30+09:00");

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        courseRedisRepository.saveCreatedEvent(42L, createdAt);

        verify(setOperations).add("course:20260723:created-events", "42");
        verify(valueOperations).set("course:42:created-at", "2026-07-23T10:15:30+09:00");
    }
}

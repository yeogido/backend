package com.yeogido.backend.domain.course.popularity.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseViewCountSyncRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;

    @Test
    void getActivityViewCountsReadsActivityZSet() {
        LocalDate date = LocalDate.of(2026, 7, 23);
        ZSetOperations.TypedTuple<String> first = typedTuple("10", 3.0);
        ZSetOperations.TypedTuple<String> second = typedTuple("20", 7.0);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeWithScores("course:activity:20260723:views", 0, -1))
                .thenReturn(Set.of(first, second));

        Map<Long, Long> result = courseViewCountSyncRedisRepository.getActivityViewCounts(date);

        assertThat(result).containsEntry(10L, 3L).containsEntry(20L, 7L);
        verify(zSetOperations).rangeWithScores("course:activity:20260723:views", 0, -1);
    }

    @Test
    void updateSyncViewCountWritesSyncKeyAndTtl() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        courseViewCountSyncRedisRepository.updateSyncViewCount(date, 42L, 9L);

        verify(zSetOperations).add("course:view-sync:20260723", "42", 9D);
        verify(stringRedisTemplate).expire("course:view-sync:20260723", Duration.ofDays(8));
    }

    private ZSetOperations.TypedTuple<String> typedTuple(String value, Double score) {
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}

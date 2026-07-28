package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePopularityRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private CoursePopularityRedisRepository coursePopularityRedisRepository;

    @Test
    void getActivitiesSumsViewsAndLikesForGivenDates() {
        LocalDate today = LocalDate.of(2026, 7, 24);
        LocalDate yesterday = today.minusDays(1);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeWithScores("course:activity:20260724:views", 0, -1))
                .thenReturn(Set.of(typedTuple("1", 10.0), typedTuple("2", 3.0)));
        when(zSetOperations.rangeWithScores("course:activity:20260724:likes", 0, -1))
                .thenReturn(Set.of(typedTuple("1", 2.0)));
        when(zSetOperations.rangeWithScores("course:activity:20260724:creates", 0, -1))
                .thenReturn(Set.of(typedTuple("2", 1.0)));
        when(zSetOperations.rangeWithScores("course:activity:20260723:views", 0, -1))
                .thenReturn(Set.of(typedTuple("1", 4.0)));
        when(zSetOperations.rangeWithScores("course:activity:20260723:likes", 0, -1))
                .thenReturn(Set.of(typedTuple("2", 5.0)));
        when(zSetOperations.rangeWithScores("course:activity:20260723:creates", 0, -1))
                .thenReturn(Set.of(typedTuple("1", 1.0)));

        Map<Long, CoursePopularityActivity> result =
                coursePopularityRedisRepository.getActivities(List.of(today, yesterday));

        assertThat(result)
                .containsEntry(1L, new CoursePopularityActivity(14L, 2L, 1L))
                .containsEntry(2L, new CoursePopularityActivity(3L, 5L, 1L));

        verify(zSetOperations).rangeWithScores("course:activity:20260724:views", 0, -1);
        verify(zSetOperations).rangeWithScores("course:activity:20260724:likes", 0, -1);
        verify(zSetOperations).rangeWithScores("course:activity:20260724:creates", 0, -1);
        verify(zSetOperations).rangeWithScores("course:activity:20260723:views", 0, -1);
        verify(zSetOperations).rangeWithScores("course:activity:20260723:likes", 0, -1);
        verify(zSetOperations).rangeWithScores("course:activity:20260723:creates", 0, -1);
    }

    private ZSetOperations.TypedTuple<String> typedTuple(String value, Double score) {
        return new DefaultTypedTuple<>(value, score);
    }
}

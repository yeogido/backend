package com.yeogido.backend.domain.course.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePopularityRankingRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private CoursePopularityRankingRedisRepository repository;

    @Test
    void replaceOfficialRankingDeletesOldRankingAndUsesPipeline() {
        repository.replaceOfficialRanking(List.of(
                new CoursePopularityRankingEntry(10L, 90L),
                new CoursePopularityRankingEntry(20L, 50L)
        ));

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("course:ranking:official");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    void replaceOfficialRegionRankingUsesRegionKey() {
        repository.replaceOfficialRegionRanking(
                3L,
                List.of(new CoursePopularityRankingEntry(10L, 90L))
        );

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("course:ranking:official:3");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    void replaceLocalRankingUsesLocalKey() {
        repository.replaceLocalRanking(
                List.of(new CoursePopularityRankingEntry(30L, 70L))
        );

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("course:ranking:local");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    void deleteOfficialRegionRankingsDeletesOnlyRegionRankingKeys() {
        when(stringRedisTemplate.keys("course:ranking:official:*"))
                .thenReturn(Set.of(
                        "course:ranking:official:1",
                        "course:ranking:official:2"
                ));

        repository.deleteOfficialRegionRankings();

        verify(stringRedisTemplate).delete(Set.of(
                "course:ranking:official:1",
                "course:ranking:official:2"
        ));
    }

    @Test
    void deleteOfficialRegionRankingsDoesNothingWhenKeysDoNotExist() {
        when(stringRedisTemplate.keys("course:ranking:official:*"))
                .thenReturn(Set.of());

        repository.deleteOfficialRegionRankings();

        verify(stringRedisTemplate, never()).delete(any(Set.class));
    }
}
package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
import com.yeogido.backend.domain.course.enums.CourseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePopularityRankingRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private CoursePopularityRankingRedisRepository repository;

    @Test
    void replaceOfficialRankingStoresTempRankingAndAtomicallyRenames() {
        repository.replaceOfficialRanking(List.of(
                new CoursePopularityRankingEntry(
                        10L,
                        90L,
                        LocalDateTime.of(2026, 7, 21, 10, 0, 0)
                ),
                new CoursePopularityRankingEntry(
                        20L,
                        50L,
                        LocalDateTime.of(2026, 7, 20, 10, 0, 0)
                )
        ));

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("course:ranking:official:tmp");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
        inOrder.verify(stringRedisTemplate)
                .rename("course:ranking:official:tmp", "course:ranking:official");

        verify(stringRedisTemplate, never()).delete("course:ranking:official");
    }

    @Test
    void replaceOfficialRankingDeletesLegacyKeyAndPublishesEmptyListIndex() {
        repository.replaceOfficialRanking(List.of());

        verify(stringRedisTemplate).delete("course:ranking:official");
        verify(stringRedisTemplate, never()).delete("course:ranking:official:tmp");
        verify(stringRedisTemplate).rename(
                argThat(key -> key.startsWith("course:ranking:official:list:tmp:")), eq("course:ranking:official:list")
        );
        verify(stringRedisTemplate, never()).rename(any(), eq("course:ranking:official"));
    }

    @Test
    void replaceLocalRankingStoresTempRankingAndAtomicallyRenames() {
        repository.replaceLocalRanking(
                List.of(new CoursePopularityRankingEntry(
                        30L,
                        70L,
                        LocalDateTime.of(2026, 7, 20, 10, 0, 0)
                ))
        );

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("course:ranking:local:tmp");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
        inOrder.verify(stringRedisTemplate)
                .rename("course:ranking:local:tmp", "course:ranking:local");

        verify(stringRedisTemplate, never()).delete("course:ranking:local");
    }

    @Test
    void replaceRankingEncodesCreatedAtInRedisMemberAndKeepsPopularityScore() {
        RedisConnection connection = mock(RedisConnection.class);
        when(stringRedisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RedisCallback<Object> callback = invocation.getArgument(0);
            callback.doInRedis(connection);
            return List.of();
        });

        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 21, 10, 0, 0, 123456000);

        repository.replaceOfficialRanking(List.of(
                new CoursePopularityRankingEntry(10L, 90L, createdAt)
        ));

        long createdAtMicros = createdAt.toEpochSecond(ZoneOffset.UTC) * 1_000_000L
                + createdAt.getNano() / 1_000L;
        String expectedMember = String.format("%020d:%d", createdAtMicros, 10L);

        verify(connection).zAdd(
                eq("course:ranking:official:tmp".getBytes(StandardCharsets.UTF_8)),
                eq(90.0d),
                eq(expectedMember.getBytes(StandardCharsets.UTF_8))
        );
        verify(connection).zAdd(
                argThat(key -> new String(key, StandardCharsets.UTF_8).startsWith("course:ranking:official:list:tmp:")),
                eq(90.0d),
                eq("0000000000000000010".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void findTopCourseIdsSupportsCompositeRedisMembers() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<String> members = new LinkedHashSet<>();
        members.add("00001758593900123456:20");
        members.add("00001758593900123455:10");
        when(zSetOperations.reverseRange("course:ranking:local", 0, 1L))
                .thenReturn(members);

        List<Long> result = repository.findTopLocalCourseIds(2);

        assertThat(result).containsExactly(20L, 10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPageMergesCourseTypesByScoreAndNumericIdAndCapsTheResult() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of("course:ranking:official:list")),
                eq("positive"), eq("5"), eq("0000000000000000015"), eq("2")))
                .thenReturn(List.of("0000000000000000010", "5", "0000000000000000002", "4"));
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of("course:ranking:local:list")),
                eq("positive"), eq("5"), eq("0000000000000000015"), eq("2")))
                .thenReturn(List.of("0000000000000000009", "5", "0000000000000000001", "3"));

        assertThat(repository.findListRankingPage(null, 5L, 15L, true, 2)).containsExactly(
                new CoursePopularityRankingRedisRepository.RankedCourse(10L, 5L),
                new CoursePopularityRankingRedisRepository.RankedCourse(9L, 5L)
        );
    }

    @Test
    void nonZeroLookupKeepsBothPositiveAndNegativeScores() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score("course:ranking:official:list", "0000000000000000001",
                "0000000000000000002", "0000000000000000003", "0000000000000000004"))
                .thenReturn(Arrays.asList(null, 0d, -5d, 5d));

        assertThat(repository.findNonZeroRankedCourseIds(CourseType.OFFICIAL, List.of(1L, 2L, 3L, 4L)))
                .containsExactlyInAnyOrder(3L, 4L);
    }
}

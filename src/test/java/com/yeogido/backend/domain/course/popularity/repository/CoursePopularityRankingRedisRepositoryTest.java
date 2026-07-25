package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private SetOperations<String, String> setOperations;

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
    void replaceOfficialRegionRankingStoresTempRegionRankingAndAtomicallyRenames() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        repository.replaceOfficialRegionRanking(
                3L,
                List.of(new CoursePopularityRankingEntry(
                        10L,
                        90L,
                        LocalDateTime.of(2026, 7, 21, 10, 0, 0)
                ))
        );

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("course:ranking:official:3:tmp");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
        inOrder.verify(stringRedisTemplate)
                .rename("course:ranking:official:3:tmp", "course:ranking:official:3");

        verify(setOperations)
                .add("course:ranking:official:regions", "3");
        verify(stringRedisTemplate, never()).delete("course:ranking:official:3");
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
    void deleteOfficialRegionRankingsDeletesOnlyRegionRankingKeys() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        when(setOperations.members("course:ranking:official:regions"))
                .thenReturn(Set.of("1", "2"));

        repository.deleteOfficialRegionRankings();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(stringRedisTemplate).delete(captor.capture());

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(
                        "course:ranking:official:1",
                        "course:ranking:official:2"
                );

        verify(stringRedisTemplate)
                .delete("course:ranking:official:regions");
    }

    @Test
    void deleteOfficialRegionRankingsDoesNothingWhenKeysDoNotExist() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        when(setOperations.members("course:ranking:official:regions"))
                .thenReturn(Set.of());

        repository.deleteOfficialRegionRankings();

        verify(stringRedisTemplate, never()).delete(any(List.class));
        verify(stringRedisTemplate, never())
                .delete("course:ranking:official:regions");
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
    }

    @Test
    void findTopCourseIdsSupportsCompositeRedisMembers() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<String> members = new LinkedHashSet<>();
        members.add("00001758593900123456:20");
        members.add("00001758593900123455:10");
        when(zSetOperations.reverseRange("course:ranking:official", 0, 1L))
                .thenReturn(members);

        List<Long> result = repository.findTopOfficialCourseIds(2);

        assertThat(result).containsExactly(20L, 10L);
    }
}

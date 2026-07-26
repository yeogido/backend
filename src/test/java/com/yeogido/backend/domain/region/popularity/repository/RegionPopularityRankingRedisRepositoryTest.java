package com.yeogido.backend.domain.region.popularity.repository;

import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionPopularityRankingRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RegionPopularityRankingRedisRepository repository;

    @Test
    void replaceRankingStoresPopularRegionRankingAndAtomicallyRenames() {
        repository.replaceRanking(List.of(
                new RegionPopularityRankingEntry(10L, 90L)
        ));

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("region:ranking:popular:tmp");
        inOrder.verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
        inOrder.verify(stringRedisTemplate)
                .rename("region:ranking:popular:tmp", "region:ranking:popular");
    }

    @Test
    void replaceRankingDeletesRankingWhenEntriesAreEmpty() {
        repository.replaceRanking(List.of());

        verify(stringRedisTemplate).delete("region:ranking:popular:tmp");
        verify(stringRedisTemplate).delete("region:ranking:popular");
        verify(stringRedisTemplate, never()).executePipelined(any(RedisCallback.class));
        verify(stringRedisTemplate, never()).rename(any(), any());
    }

    @Test
    void replaceRankingEncodesRegionIdTieBreakerForAscendingRegionId() {
        RedisConnection connection = mock(RedisConnection.class);
        when(stringRedisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RedisCallback<Object> callback = invocation.getArgument(0);
            callback.doInRedis(connection);
            return List.of();
        });

        repository.replaceRanking(List.of(
                new RegionPopularityRankingEntry(2L, 90L)
        ));

        String expectedMember = String.format("%019d:%d", Long.MAX_VALUE - 2L, 2L);

        verify(connection).zAdd(
                eq("region:ranking:popular:tmp".getBytes(StandardCharsets.UTF_8)),
                eq(90.0d),
                eq(expectedMember.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void findTopRegionIdsSupportsCompositeRedisMembers() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<String> members = new LinkedHashSet<>();
        members.add("9223372036854775805:2");
        members.add("9223372036854775804:3");
        when(zSetOperations.reverseRange("region:ranking:popular", 0, 1L))
                .thenReturn(members);

        List<Long> result = repository.findTopRegionIds(2);

        assertThat(result).containsExactly(2L, 3L);
    }
}

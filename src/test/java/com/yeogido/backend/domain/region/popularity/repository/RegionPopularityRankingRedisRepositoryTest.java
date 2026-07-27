package com.yeogido.backend.domain.region.popularity.repository;

import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionPopularityRankingRedisRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RegionPopularityRankingRedisRepository repository;

    @Test
    void replaceRankingStoresPopularRegionRankingAndAtomicallyRenames() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);

        repository.replaceRanking(List.of(
                new RegionPopularityRankingEntry(10L, 90L)
        ));

        InOrder inOrder = inOrder(stringRedisTemplate);

        inOrder.verify(stringRedisTemplate).delete("region:ranking:popular:tmp");
        inOrder.verify(stringRedisTemplate).opsForList();
        inOrder.verify(stringRedisTemplate)
                .rename("region:ranking:popular:tmp", "region:ranking:popular");
        verify(listOperations).rightPushAll("region:ranking:popular:tmp", List.of("10"));
    }

    @Test
    void replaceRankingDeletesRankingWhenEntriesAreEmpty() {
        repository.replaceRanking(List.of());

        verify(stringRedisTemplate).delete("region:ranking:popular:tmp");
        verify(stringRedisTemplate).delete("region:ranking:popular");
        verify(stringRedisTemplate, never()).opsForList();
        verify(stringRedisTemplate, never()).rename(any(), any());
    }

    @Test
    void replaceRankingStoresRegionIdsInInputOrderWithoutTieBreakerMember() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);

        repository.replaceRanking(List.of(
                new RegionPopularityRankingEntry(20L, 90L),
                new RegionPopularityRankingEntry(10L, 90L)
        ));

        verify(listOperations).rightPushAll("region:ranking:popular:tmp", List.of("20", "10"));
    }

    @Test
    void findTopRegionIdsReturnsStoredListOrder() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);

        when(listOperations.range("region:ranking:popular", 0, 1L))
                .thenReturn(List.of("20", "10"));

        List<Long> result = repository.findTopRegionIds(2);

        assertThat(result).containsExactly(20L, 10L);
    }

    @Test
    void findTopRegionIdsFallsBackToLegacyZSetDuringRedisTypeMigration() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("region:ranking:popular", 0, 1L))
                .thenThrow(new RedisSystemException("WRONGTYPE Operation against a key", null));
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

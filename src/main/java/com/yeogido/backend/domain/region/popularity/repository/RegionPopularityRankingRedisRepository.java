package com.yeogido.backend.domain.region.popularity.repository;

import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RegionPopularityRankingRedisRepository {

    private static final String DOMAIN = "region";
    private static final String RANKING = "ranking";
    private static final String POPULAR = "popular";
    private static final String TMP = "tmp";

    private final StringRedisTemplate stringRedisTemplate;

    public void replaceRanking(List<RegionPopularityRankingEntry> entries) {
        String key = rankingKey();
        String tmpKey = temporaryRankingKey(key);

        stringRedisTemplate.delete(tmpKey);

        if (entries.isEmpty()) {
            stringRedisTemplate.delete(key);
            return;
        }

        List<String> regionIds = entries.stream()
                .map(entry -> entry.regionId().toString())
                .toList();
        stringRedisTemplate.opsForList().rightPushAll(tmpKey, regionIds);

        stringRedisTemplate.rename(tmpKey, key);
    }

    public List<Long> findTopRegionIds(int size) {
        if (size <= 0) {
            return List.of();
        }

        List<String> regionIds;
        try {
            regionIds = stringRedisTemplate.opsForList()
                    .range(rankingKey(), 0, size - 1L);
        } catch (RedisSystemException exception) {
            if (!isWrongType(exception)) {
                throw exception;
            }
            return findTopLegacyZSetRegionIds(size);
        }

        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        return regionIds.stream()
                .map(Long::valueOf)
                .toList();
    }

    private List<Long> findTopLegacyZSetRegionIds(int size) {
        Set<String> regionIds = stringRedisTemplate.opsForZSet()
                .reverseRange(rankingKey(), 0, size - 1L);

        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        return regionIds.stream()
                .map(this::regionIdFromLegacyRedisMember)
                .map(Long::valueOf)
                .toList();
    }

    private String regionIdFromLegacyRedisMember(String member) {
        int separatorIndex = member.lastIndexOf(':');
        if (separatorIndex < 0) {
            return member;
        }
        return member.substring(separatorIndex + 1);
    }

    private boolean isWrongType(RedisSystemException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("WRONGTYPE");
    }

    private String temporaryRankingKey(String key) {
        return key + ":" + TMP;
    }

    private String rankingKey() {
        return RedisKey.of(DOMAIN, RANKING, POPULAR);
    }
}

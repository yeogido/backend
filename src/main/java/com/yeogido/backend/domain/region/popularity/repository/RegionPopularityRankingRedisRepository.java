package com.yeogido.backend.domain.region.popularity.repository;

import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] redisKey = serialize(tmpKey);

            for (RegionPopularityRankingEntry entry : entries) {
                connection.zAdd(
                        redisKey,
                        entry.score(),
                        serialize(redisMember(entry.regionId()))
                );
            }

            return null;
        });

        stringRedisTemplate.rename(tmpKey, key);
    }

    public List<Long> findTopRegionIds(int size) {
        if (size <= 0) {
            return List.of();
        }

        Set<String> regionIds = stringRedisTemplate.opsForZSet()
                .reverseRange(rankingKey(), 0, size - 1L);

        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        return regionIds.stream()
                .map(this::regionIdFromRedisMember)
                .map(Long::valueOf)
                .toList();
    }

    private String redisMember(Long regionId) {
        long tieBreaker = Long.MAX_VALUE - regionId;
        return String.format(Locale.ROOT, "%019d:%d", tieBreaker, regionId);
    }

    private String regionIdFromRedisMember(String member) {
        int separatorIndex = member.lastIndexOf(':');
        if (separatorIndex < 0) {
            return member;
        }
        return member.substring(separatorIndex + 1);
    }

    private byte[] serialize(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String temporaryRankingKey(String key) {
        return key + ":" + TMP;
    }

    private String rankingKey() {
        return RedisKey.of(DOMAIN, RANKING, POPULAR);
    }
}

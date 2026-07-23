package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CoursePopularityRankingRedisRepository {

    private static final String DOMAIN = "course";
    private static final String RANKING = "ranking";
    private static final String OFFICIAL = "official";
    private static final String LOCAL = "local";

    private final StringRedisTemplate stringRedisTemplate;

    public void replaceOfficialRanking(List<CoursePopularityRankingEntry> entries) {
        replaceRanking(officialRankingKey(), entries);
    }

    public void replaceOfficialRegionRanking(Long regionId, List<CoursePopularityRankingEntry> entries) {
        replaceRanking(officialRegionRankingKey(regionId), entries);
    }

    public void deleteOfficialRegionRankings() {
        Set<String> keys = stringRedisTemplate.keys(officialRegionRankingKeyPattern());

        if (keys.isEmpty()) {
            return;
        }

        stringRedisTemplate.delete(keys);
    }

    public void replaceLocalRanking(List<CoursePopularityRankingEntry> entries) {
        replaceRanking(localRankingKey(), entries);
    }

    private void replaceRanking(String key, List<CoursePopularityRankingEntry> entries) {
        stringRedisTemplate.delete(key);

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] redisKey = stringRedisTemplate.getStringSerializer().serialize(key);

            for (CoursePopularityRankingEntry entry : entries) {
                assert redisKey != null;
                connection.zAdd(
                        redisKey,
                        entry.score(),
                        Objects.requireNonNull(stringRedisTemplate.getStringSerializer()
                                .serialize(entry.courseId().toString()))
                );
            }

            return null;
        });
    }

    private String officialRankingKey() {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL);
    }

    private String officialRegionRankingKey(Long regionId) {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL, regionId.toString());
    }

    private String officialRegionRankingKeyPattern() {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL, "*");
    }

    private String localRankingKey() {
        return RedisKey.of(DOMAIN, RANKING, LOCAL);
    }
}

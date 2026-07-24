package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
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
    private static final String REGIONS = "regions";

    private final StringRedisTemplate stringRedisTemplate;

    public void replaceOfficialRanking(List<CoursePopularityRankingEntry> entries) {
        replaceRanking(officialRankingKey(), entries);
    }

    public List<Long> findTopOfficialCourseIds(int size) {
        return findTopCourseIds(officialRankingKey(), size);
    }

    public void replaceOfficialRegionRanking(Long regionId, List<CoursePopularityRankingEntry> entries) {
        replaceRanking(officialRegionRankingKey(regionId), entries);

        // 다음 재집계를 위한 지역별 Key 목록
        stringRedisTemplate.opsForSet()
                .add(officialRegionSetKey(), regionId.toString());
    }

    public List<Long> findTopOfficialRegionCourseIds(Long regionId, int size) {
        return findTopCourseIds(officialRegionRankingKey(regionId), size);
    }

    public void deleteOfficialRegionRankings() {
        Set<String> regionIds =
                stringRedisTemplate.opsForSet().members(officialRegionSetKey());

        if (regionIds == null || regionIds.isEmpty()) {
            return;
        }

        // 이전 지역별 랭킹 Key 삭제
        List<String> keys = regionIds.stream()
                .map(id -> officialRegionRankingKey(Long.valueOf(id)))
                .toList();

        stringRedisTemplate.delete(keys);
        stringRedisTemplate.delete(officialRegionSetKey());
    }

    public void replaceLocalRanking(List<CoursePopularityRankingEntry> entries) {
        replaceRanking(localRankingKey(), entries);
    }

    public List<Long> findTopLocalCourseIds(int size) {
        return findTopCourseIds(localRankingKey(), size);
    }

    private void replaceRanking(String key, List<CoursePopularityRankingEntry> entries) {
        // 최신 집계 결과로 전체 교체
        stringRedisTemplate.delete(key);

        // ZSet score를 인기 점수로 사용
        // Pipeline으로 Redis 요청 일괄 처리
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] redisKey = stringRedisTemplate.getStringSerializer().serialize(key);

            for (CoursePopularityRankingEntry entry : entries) {
                assert redisKey != null;
                connection.zAdd(
                        redisKey,
                        entry.score(),
                        Objects.requireNonNull(
                                stringRedisTemplate.getStringSerializer()
                                        .serialize(entry.courseId().toString())
                        )
                );
            }

            return null;
        });
    }

    private List<Long> findTopCourseIds(String key, int size) {
        if (size <= 0) {
            return List.of();
        }

        // score 기준 인기순 조회
        Set<String> courseIds = stringRedisTemplate.opsForZSet()
                .reverseRange(key, 0, size - 1L);

        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }

        return courseIds.stream()
                .map(Long::valueOf)
                .toList();
    }

    private String officialRankingKey() {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL);
    }

    private String officialRegionRankingKey(Long regionId) {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL, regionId.toString());
    }

    private String officialRegionSetKey() {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL, REGIONS);
    }

    private String localRankingKey() {
        return RedisKey.of(DOMAIN, RANKING, LOCAL);
    }
}

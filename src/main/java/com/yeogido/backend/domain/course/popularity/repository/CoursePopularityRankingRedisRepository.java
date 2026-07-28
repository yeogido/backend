package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
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
    private static final String TMP = "tmp";

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
        if (entries.isEmpty()) {
            stringRedisTemplate.delete(key);
            return;
        }

        String tmpKey = temporaryRankingKey(key);

        // 이전 실패로 남아있을 수 있는 임시 Key만 초기화한다.
        stringRedisTemplate.delete(tmpKey);

        // ZSet score는 인기 점수 그대로 유지하고,
        // member에 createdAt을 고정폭으로 포함해 동점 시 최신 코스가 먼저 오도록 한다.
        // Pipeline으로 임시 Key에 Redis 요청을 일괄 처리한다.
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] redisKey = serialize(tmpKey);

            for (CoursePopularityRankingEntry entry : entries) {
                connection.zAdd(
                        redisKey,
                        entry.score(),
                        serialize(redisMember(entry))
                );
            }

            return null;
        });

        stringRedisTemplate.rename(tmpKey, key);
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
                .map(this::courseIdFromRedisMember)
                .map(Long::valueOf)
                .toList();
    }

    private String redisMember(CoursePopularityRankingEntry entry) {
        LocalDateTime createdAt = Objects.requireNonNull(entry.createdAt());
        long createdAtMicros = createdAt.toEpochSecond(ZoneOffset.UTC) * 1_000_000L
                + createdAt.getNano() / 1_000L;

        // 고정폭으로 패딩해야 Redis의 문자열 역순 정렬이 시간 역순과 일치한다.
        return String.format(Locale.ROOT, "%020d:%d", createdAtMicros, entry.courseId());
    }

    private String courseIdFromRedisMember(String member) {
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

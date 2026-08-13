package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CourseViewCountSyncRedisRepository {

    private static final String DOMAIN = "course";
    private static final String ACTIVITY = "activity";
    private static final String VIEW_SYNC = "view-sync";
    private static final String VIEWS = "views";
    private static final Duration TTL = Duration.ofDays(8);

    private final StringRedisTemplate stringRedisTemplate;

    public Map<Long, Long> getActivityViewCounts(LocalDate date) {
        return getViewCounts(activityKey(date));
    }

    public Map<Long, Long> getSyncViewCounts(LocalDate date) {
        return getViewCounts(syncKey(date));
    }

    public void updateSyncViewCount(LocalDate date, Long courseId, long viewCount) {
        String key = syncKey(date);
        stringRedisTemplate.opsForZSet().add(key, courseId.toString(), viewCount);
        // 최근 7일 기준값 유지
        stringRedisTemplate.expire(key, TTL);
    }

    private Map<Long, Long> getViewCounts(String key) {
        // 활동량과 Sync 기준값의 delta 계산
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .rangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        return tuples.stream()
                .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
                .collect(Collectors.toMap(
                        tuple -> Long.valueOf(tuple.getValue()),
                        tuple -> tuple.getScore().longValue()
                ));
    }

    private String activityKey(LocalDate date) {
        // 활동량 원본 Key
        return RedisKey.dated(DOMAIN, ACTIVITY, date, VIEWS);
    }

    private String syncKey(LocalDate date) {
        // 날짜별 Sync 기준값 Key
        return RedisKey.dated(DOMAIN, VIEW_SYNC, date);
    }
}

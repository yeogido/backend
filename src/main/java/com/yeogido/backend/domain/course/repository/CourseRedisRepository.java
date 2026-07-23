package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CourseRedisRepository {

    private static final String DOMAIN = "course";
    private static final String ACTIVITY = "activity";
    private static final String VIEWS = "views";
    private static final String LIKES = "likes";
    private static final String CREATES = "creates";
    private static final Duration ACTIVITY_TTL = Duration.ofDays(8);

    private final StringRedisTemplate stringRedisTemplate;

    public void increaseViewCount(Long courseId) {
        increaseDailyViewCount(courseId, LocalDate.now());
    }

    public void increaseViewCount(Long courseId, LocalDate date) {
        increaseDailyViewCount(courseId, date);
    }

    public void increaseLikeCount(Long courseId) {
        increaseDailyLikeCount(courseId, LocalDate.now());
    }

    public void decreaseLikeCount(Long courseId) {
        decreaseDailyLikeCount(courseId, LocalDate.now());
    }

    public void saveCreatedEvent(Long courseId, LocalDate date) {
        increaseDailyActivity(courseId, date, CREATES);
    }

    public Long getViewCount(Long courseId) {
        return getDailyViewCount(courseId, LocalDate.now());
    }

    public Long getDailyViewCount(Long courseId, LocalDate date) {
        return getDailyActivityScore(courseId, date, VIEWS);
    }

    public Long getLikeCount(Long courseId) {
        return getDailyLikeCount(courseId, LocalDate.now());
    }

    public Long getDailyLikeCount(Long courseId, LocalDate date) {
        return getDailyActivityScore(courseId, date, LIKES);
    }

    public Set<String> getCreatedCourseIds(LocalDate date) {
        return stringRedisTemplate.opsForZSet()
                .range(activityKey(date, CREATES), 0, -1);
    }

    private void increaseDailyViewCount(Long courseId, LocalDate date) {
        increaseDailyActivity(courseId, date, VIEWS);
    }

    private void increaseDailyLikeCount(Long courseId, LocalDate date) {
        increaseDailyActivity(courseId, date, LIKES);
    }

    private void decreaseDailyLikeCount(Long courseId, LocalDate date) {
        increaseDailyActivity(courseId, date, LIKES, -1);
    }

    private void increaseDailyActivity(Long courseId, LocalDate date, String eventType) {
        increaseDailyActivity(courseId, date, eventType, 1);
    }

    private void increaseDailyActivity(Long courseId, LocalDate date, String eventType, int delta) {
        String key = activityKey(date, eventType);
        stringRedisTemplate.opsForZSet()
                .incrementScore(key, courseId.toString(), delta);
        stringRedisTemplate.expire(key, ACTIVITY_TTL);
    }

    private Long getDailyActivityScore(Long courseId, LocalDate date, String eventType) {
        Double score = stringRedisTemplate.opsForZSet()
                .score(activityKey(date, eventType), courseId.toString());

        return score == null ? 0L : score.longValue();
    }

    private String activityKey(LocalDate date, String eventType) {
        return RedisKey.dated(DOMAIN, ACTIVITY, date, eventType);
    }
}
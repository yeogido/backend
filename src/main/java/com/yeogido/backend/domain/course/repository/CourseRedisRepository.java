package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CourseRedisRepository {

    private static final String DOMAIN = "course";
    private static final String VIEWS = "views";
    private static final String LIKES = "likes";
    private static final String CREATED_EVENTS = "created-events";
    private static final String CREATED_AT = "created-at";

    private final StringRedisTemplate stringRedisTemplate;

    public Long increaseViewCount(Long courseId) {
        return stringRedisTemplate.opsForValue().increment(viewCountKey(courseId));
    }

    public void increaseViewCount(Long courseId, LocalDate date) {
        increaseViewCount(courseId);
        increaseDailyViewCount(courseId, date);
    }

    public Long increaseDailyViewCount(Long courseId, LocalDate date) {
        return stringRedisTemplate.opsForValue().increment(dailyViewCountKey(courseId, date));
    }

    public Long increaseLikeCount(Long courseId) {
        return stringRedisTemplate.opsForValue().increment(likeCountKey(courseId));
    }

    public Long decreaseLikeCount(Long courseId) {
        return stringRedisTemplate.opsForValue().decrement(likeCountKey(courseId));
    }

    public void saveCreatedEvent(Long courseId, OffsetDateTime createdAt) {
        stringRedisTemplate.opsForSet().add(createdEventKey(createdAt.toLocalDate()), courseId.toString());
        stringRedisTemplate.opsForValue().set(createdAtKey(courseId), createdAt.toString());
    }

    public Long getViewCount(Long courseId) {
        return getLongValue(viewCountKey(courseId));
    }

    public Long getDailyViewCount(Long courseId, LocalDate date) {
        return getLongValue(dailyViewCountKey(courseId, date));
    }

    public Long getLikeCount(Long courseId) {
        return getLongValue(likeCountKey(courseId));
    }

    public Set<String> getCreatedCourseIds(LocalDate date) {
        return stringRedisTemplate.opsForSet().members(createdEventKey(date));
    }

    private Long getLongValue(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value == null) {
            return 0L;
        }

        return Long.parseLong(value);
    }

    private String viewCountKey(Long courseId) {
        return RedisKey.of(DOMAIN, courseId.toString(), VIEWS);
    }

    private String dailyViewCountKey(Long courseId, LocalDate date) {
        return RedisKey.daily(DOMAIN, date, courseId.toString(), VIEWS);
    }

    private String likeCountKey(Long courseId) {
        return RedisKey.of(DOMAIN, courseId.toString(), LIKES);
    }

    private String createdEventKey(LocalDate date) {
        return RedisKey.daily(DOMAIN, date, CREATED_EVENTS);
    }

    private String createdAtKey(Long courseId) {
        return RedisKey.of(DOMAIN, courseId.toString(), CREATED_AT);
    }
}

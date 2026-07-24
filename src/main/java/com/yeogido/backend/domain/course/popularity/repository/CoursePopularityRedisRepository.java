package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CoursePopularityRedisRepository {

    private static final String DOMAIN = "course";
    private static final String ACTIVITY = "activity";
    private static final String VIEWS = "views";
    private static final String LIKES = "likes";

    private final StringRedisTemplate stringRedisTemplate;

    public Map<Long, CoursePopularityActivity> getActivities(List<LocalDate> dates) {
        Map<Long, MutableCoursePopularityActivity> activities = new HashMap<>();

        for (LocalDate date : dates) {
            addActivityCounts(activities, activityKey(date, VIEWS), ActivityType.VIEW);
            addActivityCounts(activities, activityKey(date, LIKES), ActivityType.LIKE);
        }

        return toActivities(activities);
    }

    private void addActivityCounts(
            Map<Long, MutableCoursePopularityActivity> activities,
            String key,
            ActivityType activityType
    ) {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .rangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return;
        }

        tuples.stream()
                .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
                .forEach(tuple -> {
                    Long courseId = Long.valueOf(tuple.getValue());
                    long count = tuple.getScore().longValue();

                    MutableCoursePopularityActivity activity =
                            activities.computeIfAbsent(courseId, ignored -> new MutableCoursePopularityActivity());

                    if (activityType == ActivityType.VIEW) {
                        activity.addViewCount(count);
                        return;
                    }

                    activity.addLikeCount(count);
                });
    }

    private Map<Long, CoursePopularityActivity> toActivities(
            Map<Long, MutableCoursePopularityActivity> activities
    ) {
        Map<Long, CoursePopularityActivity> result = new HashMap<>();

        activities.forEach((courseId, activity) ->
                result.put(courseId, activity.toActivity()));

        return result;
    }

    private String activityKey(LocalDate date, String eventType) {
        return RedisKey.dated(DOMAIN, ACTIVITY, date, eventType);
    }

    private enum ActivityType {
        VIEW,
        LIKE
    }

    private static class MutableCoursePopularityActivity {

        private long viewCount;
        private long likeCount;

        void addViewCount(long viewCount) {
            this.viewCount += viewCount;
        }

        void addLikeCount(long likeCount) {
            this.likeCount += likeCount;
        }

        CoursePopularityActivity toActivity() {
            return new CoursePopularityActivity(viewCount, likeCount);
        }
    }
}

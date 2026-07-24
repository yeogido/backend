package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import org.springframework.stereotype.Component;

@Component
public class CoursePopularityScoreCalculator {

    public static final int VIEW_COUNT_WEIGHT = 4;
    public static final int LIKE_COUNT_WEIGHT = 6;

    public long calculate(CoursePopularityActivity activity) {
        return calculate(activity.viewCount(), activity.likeCount());
    }

    public long calculate(long viewCount, long likeCount) {
        return (viewCount * VIEW_COUNT_WEIGHT) + (likeCount * LIKE_COUNT_WEIGHT);
    }
}

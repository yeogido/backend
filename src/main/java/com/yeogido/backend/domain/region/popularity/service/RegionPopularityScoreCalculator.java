package com.yeogido.backend.domain.region.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import org.springframework.stereotype.Component;

@Component
public class RegionPopularityScoreCalculator {

    public static final int VIEW_COUNT_WEIGHT = 3;
    public static final int LIKE_COUNT_WEIGHT = 5;
    public static final int CREATE_COUNT_WEIGHT = 2;

    public long calculate(CoursePopularityActivity activity) {
        return (activity.viewCount() * VIEW_COUNT_WEIGHT)
                + (activity.likeCount() * LIKE_COUNT_WEIGHT)
                + (activity.createCount() * CREATE_COUNT_WEIGHT);
    }
}

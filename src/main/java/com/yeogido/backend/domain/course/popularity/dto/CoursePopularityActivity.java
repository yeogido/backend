package com.yeogido.backend.domain.course.popularity.dto;

public record CoursePopularityActivity(
        long viewCount,
        long likeCount,
        long createCount
) {

    public CoursePopularityActivity(long viewCount, long likeCount) {
        this(viewCount, likeCount, 0L);
    }
}

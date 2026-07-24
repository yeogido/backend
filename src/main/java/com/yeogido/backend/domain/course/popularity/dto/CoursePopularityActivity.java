package com.yeogido.backend.domain.course.popularity.dto;

public record CoursePopularityActivity(
        long viewCount,
        long likeCount
) {
}

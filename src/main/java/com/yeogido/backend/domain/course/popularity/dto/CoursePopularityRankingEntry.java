package com.yeogido.backend.domain.course.popularity.dto;

public record CoursePopularityRankingEntry(
        Long courseId,
        long score
) {
}

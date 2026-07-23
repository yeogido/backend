package com.yeogido.backend.domain.course.repository;

public record CoursePopularityRankingEntry(
        Long courseId,
        long score
) {
}

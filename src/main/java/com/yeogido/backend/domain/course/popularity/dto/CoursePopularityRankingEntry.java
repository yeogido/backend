package com.yeogido.backend.domain.course.popularity.dto;

import java.time.LocalDateTime;

public record CoursePopularityRankingEntry(
        Long courseId,
        long score,
        LocalDateTime createdAt
) {
}

package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class CoursePopularityScoreService {

    private static final int POPULARITY_WINDOW_DAYS = 7;

    private final CoursePopularityRedisRepository coursePopularityRedisRepository;
    private final CoursePopularityScoreCalculator coursePopularityScoreCalculator;

    public Map<Long, Long> calculateRecentPopularityScores() {
        return calculatePopularityScores(LocalDate.now());
    }

    public Map<Long, Long> calculatePopularityScores(LocalDate baseDate) {
        List<LocalDate> dates = recentDates(baseDate);
        Map<Long, CoursePopularityActivity> activities =
                coursePopularityRedisRepository.getActivities(dates);

        Map<Long, Long> scores = new LinkedHashMap<>();
        activities.forEach((courseId, activity) ->
                scores.put(courseId, coursePopularityScoreCalculator.calculate(activity)));

        return scores;
    }

    private List<LocalDate> recentDates(LocalDate baseDate) {
        return IntStream.range(0, POPULARITY_WINDOW_DAYS)
                .mapToObj(baseDate::minusDays)
                .toList();
    }
}

package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.CoursePopularityActivity;
import com.yeogido.backend.domain.course.repository.CoursePopularityRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePopularityScoreServiceTest {

    @Mock
    private CoursePopularityRedisRepository coursePopularityRedisRepository;

    @Mock
    private CoursePopularityScoreCalculator coursePopularityScoreCalculator;

    @InjectMocks
    private CoursePopularityScoreService coursePopularityScoreService;

    @Test
    void calculatePopularityScoresReadsRecentSevenDaysAndCalculatesScores() {
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(10L, 2L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(3L, 5L);

        when(coursePopularityRedisRepository.getActivities(List.of(
                baseDate,
                baseDate.minusDays(1),
                baseDate.minusDays(2),
                baseDate.minusDays(3),
                baseDate.minusDays(4),
                baseDate.minusDays(5),
                baseDate.minusDays(6)
        ))).thenReturn(Map.of(
                1L, firstActivity,
                2L, secondActivity
        ));
        when(coursePopularityScoreCalculator.calculate(firstActivity)).thenReturn(52L);
        when(coursePopularityScoreCalculator.calculate(secondActivity)).thenReturn(42L);

        Map<Long, Long> result = coursePopularityScoreService.calculatePopularityScores(baseDate);

        assertThat(result)
                .containsEntry(1L, 52L)
                .containsEntry(2L, 42L);

        ArgumentCaptor<List<LocalDate>> datesCaptor = ArgumentCaptor.forClass(List.class);
        verify(coursePopularityRedisRepository).getActivities(datesCaptor.capture());
        assertThat(datesCaptor.getValue()).containsExactly(
                baseDate,
                baseDate.minusDays(1),
                baseDate.minusDays(2),
                baseDate.minusDays(3),
                baseDate.minusDays(4),
                baseDate.minusDays(5),
                baseDate.minusDays(6)
        );
    }
}

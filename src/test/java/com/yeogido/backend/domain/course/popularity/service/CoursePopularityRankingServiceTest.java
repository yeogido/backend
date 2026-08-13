package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePopularityRankingServiceTest {

    @Mock
    private CoursePopularityScoreService coursePopularityScoreService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePopularityRankingRedisRepository coursePopularityRankingRedisRepository;

    @InjectMocks
    private CoursePopularityRankingService coursePopularityRankingService;

    @Test
    void refreshPopularityRankingsStoresOfficialRankingsByTotal() {
        when(coursePopularityScoreService.calculateRecentPopularityScores())
                .thenReturn(Map.of(
                        1L, 30L,
                        2L, 90L,
                        3L, 50L
                ));

        when(courseRepository.findRankingTargetsByCourseIds(Map.of(
                1L, 30L,
                2L, 90L,
                3L, 50L
        ).keySet())).thenReturn(List.of(
                new RankingProjection(
                        1L,
                        CourseType.OFFICIAL,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                ),
                new RankingProjection(
                        2L,
                        CourseType.OFFICIAL,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new RankingProjection(
                        3L,
                        CourseType.OFFICIAL,
                        LocalDateTime.of(2026, 7, 22, 10, 0)
                )
        ));

        coursePopularityRankingService.refreshPopularityRankings();

        verify(coursePopularityRankingRedisRepository).replaceOfficialRanking(List.of(
                new CoursePopularityRankingEntry(
                        2L,
                        90L,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new CoursePopularityRankingEntry(
                        3L,
                        50L,
                        LocalDateTime.of(2026, 7, 22, 10, 0)
                ),
                new CoursePopularityRankingEntry(
                        1L,
                        30L,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                )
        ));

        verify(coursePopularityRankingRedisRepository).replaceLocalRanking(List.of());
    }

    @Test
    void refreshPopularityRankingsStoresLocalRankingsByTotal() {
        when(coursePopularityScoreService.calculateRecentPopularityScores())
                .thenReturn(Map.of(
                        1L, 80L,
                        2L, 20L
                ));

        when(courseRepository.findRankingTargetsByCourseIds(Map.of(
                1L, 80L,
                2L, 20L
        ).keySet())).thenReturn(List.of(
                new RankingProjection(
                        1L,
                        CourseType.LOCAL,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new RankingProjection(
                        2L,
                        CourseType.LOCAL,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                )
        ));

        coursePopularityRankingService.refreshPopularityRankings();

        verify(coursePopularityRankingRedisRepository).replaceOfficialRanking(List.of());

        verify(coursePopularityRankingRedisRepository).replaceLocalRanking(List.of(
                new CoursePopularityRankingEntry(
                        1L,
                        80L,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new CoursePopularityRankingEntry(
                        2L,
                        20L,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                )
        ));

    }

    @Test
    void refreshPopularityRankingsSortsByCreatedAtWhenScoresAreEqual() {
        when(coursePopularityScoreService.calculateRecentPopularityScores())
                .thenReturn(Map.of(
                        1L, 100L,
                        2L, 100L
                ));

        when(courseRepository.findRankingTargetsByCourseIds(Map.of(
                1L, 100L,
                2L, 100L
        ).keySet())).thenReturn(List.of(
                new RankingProjection(
                        1L,
                        CourseType.LOCAL,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                ),
                new RankingProjection(
                        2L,
                        CourseType.LOCAL,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                )
        ));

        coursePopularityRankingService.refreshPopularityRankings();

        verify(coursePopularityRankingRedisRepository).replaceLocalRanking(List.of(
                new CoursePopularityRankingEntry(
                        2L,
                        100L,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new CoursePopularityRankingEntry(
                        1L,
                        100L,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                )
        ));
    }

    private record RankingProjection(
            Long courseId,
            CourseType courseType,
            LocalDateTime createdAt
    ) implements CourseRepository.CourseRankingProjection {

        @Override
        public Long getCourseId() {
            return courseId;
        }

        @Override
        public CourseType getCourseType() {
            return courseType;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}

package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.repository.CoursePopularityRankingEntry;
import com.yeogido.backend.domain.course.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.never;
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
    void refreshPopularityRankingsStoresOfficialRankingsByTotalAndRegion() {
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
                        10L,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                ),
                new RankingProjection(
                        2L,
                        CourseType.OFFICIAL,
                        10L,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new RankingProjection(
                        3L,
                        CourseType.OFFICIAL,
                        20L,
                        LocalDateTime.of(2026, 7, 22, 10, 0)
                )
        ));

        coursePopularityRankingService.refreshPopularityRankings();

        verify(coursePopularityRankingRedisRepository).replaceOfficialRanking(List.of(
                new CoursePopularityRankingEntry(2L, 90L),
                new CoursePopularityRankingEntry(3L, 50L),
                new CoursePopularityRankingEntry(1L, 30L)
        ));

        verify(coursePopularityRankingRedisRepository).deleteOfficialRegionRankings();

        verify(coursePopularityRankingRedisRepository).replaceOfficialRegionRanking(
                10L,
                List.of(
                        new CoursePopularityRankingEntry(2L, 90L),
                        new CoursePopularityRankingEntry(1L, 30L)
                )
        );

        verify(coursePopularityRankingRedisRepository).replaceOfficialRegionRanking(
                20L,
                List.of(
                        new CoursePopularityRankingEntry(3L, 50L)
                )
        );

        verify(coursePopularityRankingRedisRepository).replaceLocalRanking(List.of());
    }

    @Test
    void refreshPopularityRankingsStoresOnlyTotalRankingForLocalCourses() {
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
                        10L,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                ),
                new RankingProjection(
                        2L,
                        CourseType.LOCAL,
                        20L,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                )
        ));

        coursePopularityRankingService.refreshPopularityRankings();

        verify(coursePopularityRankingRedisRepository).replaceOfficialRanking(List.of());

        verify(coursePopularityRankingRedisRepository).deleteOfficialRegionRankings();

        verify(coursePopularityRankingRedisRepository, never())
                .replaceOfficialRegionRanking(
                        10L,
                        List.of(new CoursePopularityRankingEntry(1L, 80L))
                );

        verify(coursePopularityRankingRedisRepository).replaceLocalRanking(List.of(
                new CoursePopularityRankingEntry(1L, 80L),
                new CoursePopularityRankingEntry(2L, 20L)
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
                        10L,
                        LocalDateTime.of(2026, 7, 20, 10, 0)
                ),
                new RankingProjection(
                        2L,
                        CourseType.LOCAL,
                        10L,
                        LocalDateTime.of(2026, 7, 21, 10, 0)
                )
        ));

        coursePopularityRankingService.refreshPopularityRankings();

        verify(coursePopularityRankingRedisRepository).replaceLocalRanking(List.of(
                new CoursePopularityRankingEntry(2L, 100L),
                new CoursePopularityRankingEntry(1L, 100L)
        ));
    }

    private record RankingProjection(
            Long courseId,
            CourseType courseType,
            Long regionId,
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
        public Long getRegionId() {
            return regionId;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}
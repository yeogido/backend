package com.yeogido.backend.domain.region.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import com.yeogido.backend.domain.region.popularity.repository.RegionPopularityRankingRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionPopularityRankingServiceTest {

    @Mock
    private CoursePopularityRedisRepository coursePopularityRedisRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private RegionPopularityScoreCalculator regionPopularityScoreCalculator;

    @Mock
    private RegionPopularityRankingRedisRepository regionPopularityRankingRedisRepository;

    @InjectMocks
    private RegionPopularityRankingService regionPopularityRankingService;

    @Test
    void refreshPopularityRankingsAggregatesCourseActivitiesByCityCountyRegion() {
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(10L, 2L, 1L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(4L, 1L, 1L);
        CoursePopularityActivity thirdActivity = new CoursePopularityActivity(6L, 4L, 1L);

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
                2L, secondActivity,
                3L, thirdActivity
        ));

        when(courseRepository.findRegionPopularityTargetsByCourseIds(Map.of(
                1L, firstActivity,
                2L, secondActivity,
                3L, thirdActivity
        ).keySet())).thenReturn(List.of(
                new TargetProjection(1L, 20L, "전주시", "전주시", RegionType.SUB_REGION),
                new TargetProjection(2L, 20L, "전주시", "전주시", RegionType.SUB_REGION),
                new TargetProjection(3L, 30L, "강남구", "강남구", RegionType.SUB_REGION)
        ));

        when(regionPopularityScoreCalculator.calculate(firstActivity)).thenReturn(42L);
        when(regionPopularityScoreCalculator.calculate(secondActivity)).thenReturn(19L);

        regionPopularityRankingService.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of(
                new RegionPopularityRankingEntry(20L, 61L)
        ));
    }

    @Test
    void refreshPopularityRankingsIncludesJejuRegionAndSortsTieByRegionIdAsc() {
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(1L, 0L, 0L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(1L, 0L, 0L);

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

        when(courseRepository.findRegionPopularityTargetsByCourseIds(Map.of(
                1L, firstActivity,
                2L, secondActivity
        ).keySet())).thenReturn(List.of(
                new TargetProjection(1L, 50L, "제주", "제주특별자치도", RegionType.REGION),
                new TargetProjection(2L, 10L, "여수시", "여수시", RegionType.SUB_REGION)
        ));

        when(regionPopularityScoreCalculator.calculate(firstActivity)).thenReturn(30L);
        when(regionPopularityScoreCalculator.calculate(secondActivity)).thenReturn(30L);

        regionPopularityRankingService.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of(
                new RegionPopularityRankingEntry(10L, 30L),
                new RegionPopularityRankingEntry(50L, 30L)
        ));
    }

    @Test
    void refreshPopularityRankingsStoresEmptyRankingWhenActivityIsEmpty() {
        LocalDate baseDate = LocalDate.of(2026, 7, 24);

        when(coursePopularityRedisRepository.getActivities(List.of(
                baseDate,
                baseDate.minusDays(1),
                baseDate.minusDays(2),
                baseDate.minusDays(3),
                baseDate.minusDays(4),
                baseDate.minusDays(5),
                baseDate.minusDays(6)
        ))).thenReturn(Map.of());

        regionPopularityRankingService.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of());
    }

    private record TargetProjection(
            Long courseId,
            Long regionId,
            String regionName,
            String regionFullName,
            RegionType regionType
    ) implements CourseRepository.RegionPopularityTargetProjection {

        @Override
        public Long getCourseId() {
            return courseId;
        }

        @Override
        public Long getRegionId() {
            return regionId;
        }

        @Override
        public String getRegionName() {
            return regionName;
        }

        @Override
        public String getRegionFullName() {
            return regionFullName;
        }

        @Override
        public RegionType getRegionType() {
            return regionType;
        }
    }
}

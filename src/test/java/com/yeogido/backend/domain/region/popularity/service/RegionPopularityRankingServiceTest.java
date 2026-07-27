package com.yeogido.backend.domain.region.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import com.yeogido.backend.domain.region.popularity.repository.RegionPopularityRankingRedisRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionPopularityRankingServiceTest {

    @Mock
    private CoursePopularityRedisRepository coursePopularityRedisRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private RegionPopularityScoreCalculator regionPopularityScoreCalculator;

    @Mock
    private RegionPopularityRankingRedisRepository regionPopularityRankingRedisRepository;

    @InjectMocks
    private RegionPopularityRankingService regionPopularityRankingService;

    @Test
    void refreshPopularityRankingsAggregatesCourseActivitiesAndIncludesZeroScoreRegions() {
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(10L, 2L, 1L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(4L, 1L, 1L);
        CoursePopularityActivity thirdActivity = new CoursePopularityActivity(6L, 4L, 1L);

        when(regionRepository.findAll()).thenReturn(List.of(
                region(20L),
                region(30L),
                region(40L)
        ));

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
        when(regionPopularityScoreCalculator.calculate(thirdActivity)).thenReturn(31L);

        regionPopularityRankingService.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of(
                new RegionPopularityRankingEntry(20L, 61L),
                new RegionPopularityRankingEntry(30L, 31L),
                new RegionPopularityRankingEntry(40L, 0L)
        ));
    }

    @Test
    void refreshPopularityRankingsIncludesJejuRegionAndShufflesOnlyEqualScoreGroups() {
        RegionPopularityRankingService service = new RegionPopularityRankingService(
                coursePopularityRedisRepository,
                courseRepository,
                regionRepository,
                regionPopularityScoreCalculator,
                regionPopularityRankingRedisRepository
        ) {
            @Override
            void shuffleEqualScoreGroup(List<RegionPopularityRankingEntry> entries) {
                Collections.reverse(entries);
            }
        };
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(1L, 0L, 0L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(2L, 0L, 0L);
        CoursePopularityActivity thirdActivity = new CoursePopularityActivity(3L, 0L, 0L);
        CoursePopularityActivity fourthActivity = new CoursePopularityActivity(4L, 0L, 0L);

        when(regionRepository.findAll()).thenReturn(List.of(
                region(50L),
                region(10L),
                region(20L),
                region(30L)
        ));

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
                3L, thirdActivity,
                4L, fourthActivity
        ));

        when(courseRepository.findRegionPopularityTargetsByCourseIds(Map.of(
                1L, firstActivity,
                2L, secondActivity,
                3L, thirdActivity,
                4L, fourthActivity
        ).keySet())).thenReturn(List.of(
                new TargetProjection(1L, 50L, "제주", "제주특별자치도", RegionType.REGION),
                new TargetProjection(2L, 10L, "여수시", "여수시", RegionType.SUB_REGION),
                new TargetProjection(3L, 20L, "전주시", "전주시", RegionType.SUB_REGION),
                new TargetProjection(4L, 30L, "강릉시", "강릉시", RegionType.SUB_REGION)
        ));

        when(regionPopularityScoreCalculator.calculate(firstActivity)).thenReturn(30L);
        when(regionPopularityScoreCalculator.calculate(secondActivity)).thenReturn(30L);
        when(regionPopularityScoreCalculator.calculate(thirdActivity)).thenReturn(50L);
        when(regionPopularityScoreCalculator.calculate(fourthActivity)).thenReturn(50L);

        service.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of(
                new RegionPopularityRankingEntry(30L, 50L),
                new RegionPopularityRankingEntry(20L, 50L),
                new RegionPopularityRankingEntry(10L, 30L),
                new RegionPopularityRankingEntry(50L, 30L)
        ));
    }

    @Test
    void refreshPopularityRankingsPassesTieGroupToShuffleBeforeSaving() {
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(1L, 0L, 0L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(1L, 0L, 0L);

        when(regionRepository.findAll()).thenReturn(List.of(
                region(50L),
                region(10L)
        ));
        when(coursePopularityRedisRepository.getActivities(any())).thenReturn(Map.of(
                1L, firstActivity,
                2L, secondActivity
        ));
        when(courseRepository.findRegionPopularityTargetsByCourseIds(any())).thenReturn(List.of(
                new TargetProjection(1L, 50L, "제주", "제주특별자치도", RegionType.REGION),
                new TargetProjection(2L, 10L, "여수시", "여수시", RegionType.SUB_REGION)
        ));
        when(regionPopularityScoreCalculator.calculate(firstActivity)).thenReturn(30L);
        when(regionPopularityScoreCalculator.calculate(secondActivity)).thenReturn(30L);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<RegionPopularityRankingEntry> entries = invocation.getArgument(0);
            assertThat(entries)
                    .containsExactlyInAnyOrder(
                            new RegionPopularityRankingEntry(50L, 30L),
                            new RegionPopularityRankingEntry(10L, 30L)
                    );
            return null;
        }).when(regionPopularityRankingRedisRepository).replaceRanking(any());

        regionPopularityRankingService.refreshPopularityRankings(baseDate);

        verify(coursePopularityRedisRepository).getActivities(eq(List.of(
                baseDate,
                baseDate.minusDays(1),
                baseDate.minusDays(2),
                baseDate.minusDays(3),
                baseDate.minusDays(4),
                baseDate.minusDays(5),
                baseDate.minusDays(6)
        )));
    }

    @Test
    void refreshPopularityRankingsStoresAllRegionsWithZeroScoreWhenActivityIsEmpty() {
        RegionPopularityRankingService service = new RegionPopularityRankingService(
                coursePopularityRedisRepository,
                courseRepository,
                regionRepository,
                regionPopularityScoreCalculator,
                regionPopularityRankingRedisRepository
        ) {
            @Override
            void shuffleEqualScoreGroup(List<RegionPopularityRankingEntry> entries) {
                Collections.reverse(entries);
            }
        };
        LocalDate baseDate = LocalDate.of(2026, 7, 24);

        when(regionRepository.findAll()).thenReturn(List.of(
                region(10L),
                region(20L),
                region(30L)
        ));
        when(coursePopularityRedisRepository.getActivities(List.of(
                baseDate,
                baseDate.minusDays(1),
                baseDate.minusDays(2),
                baseDate.minusDays(3),
                baseDate.minusDays(4),
                baseDate.minusDays(5),
                baseDate.minusDays(6)
        ))).thenReturn(Map.of());

        service.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of(
                new RegionPopularityRankingEntry(30L, 0L),
                new RegionPopularityRankingEntry(20L, 0L),
                new RegionPopularityRankingEntry(10L, 0L)
        ));
        verify(courseRepository, never()).findRegionPopularityTargetsByCourseIds(any());
    }

    @Test
    void refreshPopularityRankingsIncludesEveryRegionInEqualScoreGroups() {
        RegionPopularityRankingService service = new RegionPopularityRankingService(
                coursePopularityRedisRepository,
                courseRepository,
                regionRepository,
                regionPopularityScoreCalculator,
                regionPopularityRankingRedisRepository
        ) {
            @Override
            void shuffleEqualScoreGroup(List<RegionPopularityRankingEntry> entries) {
                Collections.reverse(entries);
            }
        };
        LocalDate baseDate = LocalDate.of(2026, 7, 24);
        CoursePopularityActivity firstActivity = new CoursePopularityActivity(1L, 0L, 0L);
        CoursePopularityActivity secondActivity = new CoursePopularityActivity(2L, 0L, 0L);

        when(regionRepository.findAll()).thenReturn(List.of(
                region(10L),
                region(20L),
                region(30L),
                region(40L)
        ));
        when(coursePopularityRedisRepository.getActivities(any())).thenReturn(Map.of(
                1L, firstActivity,
                2L, secondActivity
        ));
        when(courseRepository.findRegionPopularityTargetsByCourseIds(any())).thenReturn(List.of(
                new TargetProjection(1L, 10L, "서울특별시", "서울특별시", RegionType.REGION),
                new TargetProjection(2L, 20L, "부산광역시", "부산광역시", RegionType.REGION)
        ));
        when(regionPopularityScoreCalculator.calculate(firstActivity)).thenReturn(30L);
        when(regionPopularityScoreCalculator.calculate(secondActivity)).thenReturn(30L);

        service.refreshPopularityRankings(baseDate);

        verify(regionPopularityRankingRedisRepository).replaceRanking(List.of(
                new RegionPopularityRankingEntry(20L, 30L),
                new RegionPopularityRankingEntry(10L, 30L),
                new RegionPopularityRankingEntry(40L, 0L),
                new RegionPopularityRankingEntry(30L, 0L)
        ));
    }

    private Region region(Long id) {
        return Region.builder()
                .id(id)
                .name("region-" + id)
                .fullName("region-" + id)
                .type(RegionType.REGION)
                .build();
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

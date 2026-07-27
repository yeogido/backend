package com.yeogido.backend.domain.region.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import com.yeogido.backend.domain.region.popularity.repository.RegionPopularityRankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RegionPopularityRankingService {

    private static final int POPULARITY_WINDOW_DAYS = 7;

    private final CoursePopularityRedisRepository coursePopularityRedisRepository;
    private final CourseRepository courseRepository;
    private final RegionPopularityScoreCalculator regionPopularityScoreCalculator;
    private final RegionPopularityRankingRedisRepository regionPopularityRankingRedisRepository;

    public void refreshPopularityRankings() {
        refreshPopularityRankings(LocalDate.now());
    }

    public void refreshPopularityRankings(LocalDate baseDate) {
        Map<Long, CoursePopularityActivity> activities =
                coursePopularityRedisRepository.getActivities(recentDates(baseDate));

        if (activities.isEmpty()) {
            regionPopularityRankingRedisRepository.replaceRanking(List.of());
            return;
        }

        List<CourseRepository.RegionPopularityTargetProjection> targets =
                courseRepository.findRegionPopularityTargetsByCourseIds(activities.keySet());

        Map<Long, Long> scoresByRegion = new LinkedHashMap<>();

        for (CourseRepository.RegionPopularityTargetProjection target : targets) {
            if (!isRankingTargetRegion(target)) {
                continue;
            }

            CoursePopularityActivity activity = activities.get(target.getCourseId());
            if (activity == null) {
                continue;
            }

            scoresByRegion.merge(
                    target.getRegionId(),
                    regionPopularityScoreCalculator.calculate(activity),
                    Long::sum
            );
        }

        regionPopularityRankingRedisRepository.replaceRanking(toRankingEntries(scoresByRegion));
    }

    private List<LocalDate> recentDates(LocalDate baseDate) {
        return IntStream.range(0, POPULARITY_WINDOW_DAYS)
                .mapToObj(baseDate::minusDays)
                .toList();
    }

    private boolean isRankingTargetRegion(CourseRepository.RegionPopularityTargetProjection target) {
        if (target.getRegionType() == RegionType.SUB_REGION) {
            return isCityOrCounty(target.getRegionName());
        }

        return target.getRegionType() == RegionType.REGION && isJeju(target);
    }

    private boolean isCityOrCounty(String regionName) {
        return regionName != null && (regionName.endsWith("시") || regionName.endsWith("군"));
    }

    private boolean isJeju(CourseRepository.RegionPopularityTargetProjection target) {
        return "제주특별자치도".equals(target.getRegionFullName())
                || "제주도".equals(target.getRegionFullName())
                || "제주".equals(target.getRegionName());
    }

    private List<RegionPopularityRankingEntry> toRankingEntries(Map<Long, Long> scoresByRegion) {
        List<RegionPopularityRankingEntry> entries = scoresByRegion.entrySet()
                .stream()
                .map(entry -> new RegionPopularityRankingEntry(entry.getKey(), entry.getValue()))
                .toList();

        List<RegionPopularityRankingEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(RegionPopularityRankingEntry::score).reversed());

        int groupStart = 0;
        while (groupStart < sortedEntries.size()) {
            int groupEnd = groupStart + 1;
            long score = sortedEntries.get(groupStart).score();

            while (groupEnd < sortedEntries.size() && sortedEntries.get(groupEnd).score() == score) {
                groupEnd++;
            }

            shuffleEqualScoreGroup(sortedEntries.subList(groupStart, groupEnd));
            groupStart = groupEnd;
        }

        return sortedEntries;
    }

    void shuffleEqualScoreGroup(List<RegionPopularityRankingEntry> entries) {
        Collections.shuffle(entries);
    }
}

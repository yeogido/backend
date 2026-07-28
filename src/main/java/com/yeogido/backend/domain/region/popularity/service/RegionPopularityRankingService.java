package com.yeogido.backend.domain.region.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.popularity.dto.RegionPopularityRankingEntry;
import com.yeogido.backend.domain.region.popularity.repository.RegionPopularityRankingRedisRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
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
    private final RegionRepository regionRepository;
    private final RegionPopularityScoreCalculator regionPopularityScoreCalculator;
    private final RegionPopularityRankingRedisRepository regionPopularityRankingRedisRepository;

    public void refreshPopularityRankings() {
        refreshPopularityRankings(LocalDate.now());
    }

    public void refreshPopularityRankings(LocalDate baseDate) {
        Map<Long, Long> scoresByRegion = initialScoresByRegion();
        Map<Long, CoursePopularityActivity> activities =
                coursePopularityRedisRepository.getActivities(recentDates(baseDate));

        if (activities.isEmpty()) {
            regionPopularityRankingRedisRepository.replaceRanking(toRankingEntries(scoresByRegion));
            return;
        }

        List<CourseRepository.RegionPopularityTargetProjection> targets =
                courseRepository.findRegionPopularityTargetsByCourseIds(activities.keySet());

        for (CourseRepository.RegionPopularityTargetProjection target : targets) {
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

    private Map<Long, Long> initialScoresByRegion() {
        Map<Long, Long> scoresByRegion = new LinkedHashMap<>();

        for (Region region : regionRepository.findAll()) {
            scoresByRegion.put(region.getId(), 0L);
        }

        return scoresByRegion;
    }

    private List<LocalDate> recentDates(LocalDate baseDate) {
        return IntStream.range(0, POPULARITY_WINDOW_DAYS)
                .mapToObj(baseDate::minusDays)
                .toList();
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

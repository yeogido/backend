package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.repository.CoursePopularityRankingEntry;
import com.yeogido.backend.domain.course.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoursePopularityRankingService {

    private final CoursePopularityScoreService coursePopularityScoreService;
    private final CourseRepository courseRepository;
    private final CoursePopularityRankingRedisRepository coursePopularityRankingRedisRepository;

    public void refreshPopularityRankings() {
        Map<Long, Long> scores = coursePopularityScoreService.calculateRecentPopularityScores();

        if (scores.isEmpty()) {
            coursePopularityRankingRedisRepository.replaceOfficialRanking(List.of());
            coursePopularityRankingRedisRepository.deleteOfficialRegionRankings();
            coursePopularityRankingRedisRepository.replaceLocalRanking(List.of());
            return;
        }

        List<CourseRepository.CourseRankingProjection> courses =
                courseRepository.findRankingTargetsByCourseIds(scores.keySet());

        List<CourseRankingTarget> officialTargets = filterTargets(courses, CourseType.OFFICIAL, scores);
        List<CourseRankingTarget> localTargets = filterTargets(courses, CourseType.LOCAL, scores);

        coursePopularityRankingRedisRepository.replaceOfficialRanking(toRankingEntries(officialTargets));
        coursePopularityRankingRedisRepository.deleteOfficialRegionRankings();
        replaceOfficialRegionRankings(officialTargets);
        coursePopularityRankingRedisRepository.replaceLocalRanking(toRankingEntries(localTargets));
    }

    private List<CourseRankingTarget> filterTargets(
            List<CourseRepository.CourseRankingProjection> courses,
            CourseType courseType,
            Map<Long, Long> scores
    ) {
        return courses.stream()
                .filter(course -> course.getCourseType() == courseType)
                .map(course -> new CourseRankingTarget(
                        course.getCourseId(),
                        course.getRegionId(),
                        scores.get(course.getCourseId())
                ))
                .filter(target -> target.score() != null)
                .toList();
    }

    private void replaceOfficialRegionRankings(List<CourseRankingTarget> officialTargets) {
        Map<Long, List<CourseRankingTarget>> targetsByRegion = officialTargets.stream()
                .collect(Collectors.groupingBy(CourseRankingTarget::regionId));

        targetsByRegion.forEach((regionId, targets) ->
                coursePopularityRankingRedisRepository.replaceOfficialRegionRanking(
                        regionId,
                        toRankingEntries(targets)
                ));
    }

    private List<CoursePopularityRankingEntry> toRankingEntries(List<CourseRankingTarget> targets) {
        return targets.stream()
                .sorted(Comparator.comparing(CourseRankingTarget::score).reversed()
                        .thenComparing(CourseRankingTarget::courseId))
                .map(target -> new CoursePopularityRankingEntry(target.courseId(), target.score()))
                .toList();
    }

    private record CourseRankingTarget(
            Long courseId,
            Long regionId,
            Long score
    ) {
    }
}

package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CoursePopularityRankingService {

    private final CoursePopularityScoreService coursePopularityScoreService;
    private final CourseRepository courseRepository;
    private final CoursePopularityRankingRedisRepository coursePopularityRankingRedisRepository;

    public void refreshPopularityRankings() {
        // 조회용 랭킹 스냅샷 생성
        Map<Long, Long> scores = coursePopularityScoreService.calculateRecentPopularityScores();

        if (scores.isEmpty()) {
            coursePopularityRankingRedisRepository.replaceOfficialRanking(List.of());
            coursePopularityRankingRedisRepository.replaceLocalRanking(List.of());
            return;
        }

        List<CourseRepository.CourseRankingProjection> courses =
                courseRepository.findRankingTargetsByCourseIds(scores.keySet());

        List<CourseRankingTarget> officialTargets = filterTargets(courses, CourseType.OFFICIAL, scores);
        List<CourseRankingTarget> localTargets = filterTargets(courses, CourseType.LOCAL, scores);

        coursePopularityRankingRedisRepository.replaceOfficialRanking(toRankingEntries(officialTargets));
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
                        scores.get(course.getCourseId()),
                        course.getCreatedAt()
                ))
                .filter(target -> target.score() != null)
                .toList();
    }

    private List<CoursePopularityRankingEntry> toRankingEntries(List<CourseRankingTarget> targets) {
        // 점수 내림차순, 최신 코스 우선
        return targets.stream()
                .sorted(
                        Comparator.comparing(CourseRankingTarget::score)
                                .reversed()
                                .thenComparing(
                                        CourseRankingTarget::createdAt,
                                        Comparator.reverseOrder()
                                )
                )
                .map(target -> new CoursePopularityRankingEntry(
                        target.courseId(),
                        target.score(),
                        target.createdAt()
                ))
                .toList();
    }

    private record CourseRankingTarget(
            Long courseId,
            Long score,
            LocalDateTime createdAt
    ) {
    }
}

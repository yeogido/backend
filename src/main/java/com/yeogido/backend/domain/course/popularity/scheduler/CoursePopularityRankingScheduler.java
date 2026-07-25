package com.yeogido.backend.domain.course.popularity.scheduler;

import com.yeogido.backend.domain.course.popularity.service.CoursePopularityRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("!test")
@Component
@RequiredArgsConstructor
public class CoursePopularityRankingScheduler {

    private final CoursePopularityRankingService coursePopularityRankingService;

    @Scheduled(cron = "0 0 * * * *")
    public void refreshPopularityRankings() {
        // 조회용 인기 랭킹 갱신
        coursePopularityRankingService.refreshPopularityRankings();
    }
}

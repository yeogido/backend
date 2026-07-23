package com.yeogido.backend.domain.course.scheduler;

import com.yeogido.backend.domain.course.service.CoursePopularityRankingService;
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
        coursePopularityRankingService.refreshPopularityRankings();
    }
}

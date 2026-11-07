package com.yeogido.backend.domain.region.popularity.scheduler;

import com.yeogido.backend.domain.region.popularity.service.RegionPopularityRankingService;
import com.yeogido.backend.global.logging.ScheduledExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("!test")
@Component
@RequiredArgsConstructor
public class RegionPopularityRankingScheduler {

    private final RegionPopularityRankingService regionPopularityRankingService;

    @Scheduled(cron = "0 0 * * * *")
    public void refreshPopularityRankings() {
        try (var execution = ScheduledExecution.start("regionPopularityRanking")) {
            try {
                regionPopularityRankingService.refreshPopularityRankings();
                execution.complete();
            } catch (RuntimeException | Error failure) {
                execution.failed(failure);
                throw failure;
            }
        }
    }
}

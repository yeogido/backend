package com.yeogido.backend.domain.region.popularity.scheduler;

import com.yeogido.backend.domain.region.popularity.service.RegionPopularityRankingService;
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
        regionPopularityRankingService.refreshPopularityRankings();
    }
}

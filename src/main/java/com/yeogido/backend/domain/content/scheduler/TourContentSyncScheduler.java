package com.yeogido.backend.domain.content.scheduler;

import com.yeogido.backend.domain.content.service.TourContentSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("!test")
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.tour-api",
        name = "sync-enabled",
        havingValue = "true"
)
public class TourContentSyncScheduler {

    private final TourContentSyncService tourContentSyncService;

    @Scheduled(cron = "${app.tour-api.sync-cron:0 0 4 * * *}")
    public void synchronizeTourContents() {
        tourContentSyncService.synchronize();
    }
}

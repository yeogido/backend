package com.yeogido.backend.domain.content.scheduler;

import com.yeogido.backend.domain.content.service.TourContentSyncService;
import com.yeogido.backend.global.logging.ScheduledExecution;
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
        try (var execution = ScheduledExecution.start("tour.content.sync")) {
            var report = tourContentSyncService.synchronizeWithReport();
            if (report != null && report.result() != null) {
                var result = report.result();
                execution.counts(result.receivedCount(), result.createdCount() + result.updatedCount(),
                        report.failedItemCount());
                // 처리 실패와 중복되지 않는 정상 제외 항목 수 집계
                execution.count("skippedCount", result.skippedCount() - report.failedItemCount());
                execution.count("detailFailureCount", report.detailFailureCount());
                if (report.detailFailureCount() > 0) {
                    execution.completePartialFailure();
                } else {
                    execution.complete();
                }
            } else {
                execution.complete();
            }
        }
    }
}

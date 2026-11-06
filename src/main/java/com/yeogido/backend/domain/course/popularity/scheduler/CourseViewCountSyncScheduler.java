package com.yeogido.backend.domain.course.popularity.scheduler;

import com.yeogido.backend.domain.course.popularity.service.CourseViewCountSyncService;
import com.yeogido.backend.global.logging.ScheduledExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("!test")
@Component
@RequiredArgsConstructor
public class CourseViewCountSyncScheduler {

    private final CourseViewCountSyncService courseViewCountSyncService;

    @Scheduled(cron = "0 0 * * * *")
    public void syncCourseViewCounts() {
        try (var execution = ScheduledExecution.start("course.view_count.sync")) {
            // Redis 조회수 배치 동기화
            var result = courseViewCountSyncService.syncRecentViewCounts();
            if (result == null) {
                execution.complete();
                return;
            }
            execution.counts(result.processedCount(), result.successCount(), result.failedCount());
            execution.count("failedDateCount", result.failedDateCount());
            execution.count("completedDateCount", result.completedDateCount());
            if (result.allFailed()) {
                execution.completeFailed();
            } else if (result.hasFailures()) {
                execution.completePartialFailure();
            } else {
                execution.complete();
            }
        }
    }
}

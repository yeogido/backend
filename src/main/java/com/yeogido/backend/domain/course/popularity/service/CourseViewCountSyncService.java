package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.popularity.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.global.logging.SafeLogDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseViewCountSyncService {

    private static final int SYNC_WINDOW_DAYS = 7;

    private final CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;
    private final CourseViewCountSyncTransactionService transactionService;

    public SyncResult syncRecentViewCounts() {
        LocalDate today = LocalDate.now();
        ItemCounts counts = new ItemCounts();
        int completedDateCount = 0;
        int failedDateCount = 0;

        // 최근 7일 조회수 동기화
        for (int i = 0; i < SYNC_WINDOW_DAYS; i++) {
            LocalDate date = today.minusDays(i);

            try {
                syncViewCounts(date, counts);
                completedDateCount++;
            } catch (Exception exception) {
                failedDateCount++;
                log.atWarn().addKeyValue("event", "course.view_count.date.failed")
                        .addKeyValue("date", date)
                        .addKeyValue("failureType", SafeLogDetails.failureType(exception))
                        .addKeyValue("exceptionType", exception.getClass().getName())
                        .log("Course view count date synchronization failed.");
            }
        }
        return new SyncResult(counts.successCount + counts.failedCount, counts.successCount, counts.failedCount,
                completedDateCount, failedDateCount);
    }

    private void syncViewCounts(LocalDate date, ItemCounts counts) {
        Map<Long, Long> activityViewCounts =
                courseViewCountSyncRedisRepository.getActivityViewCounts(date);

        if (activityViewCounts.isEmpty()) {
            return;
        }

        Map<Long, Long> syncedViewCounts =
                courseViewCountSyncRedisRepository.getSyncViewCounts(date);

        for (Map.Entry<Long, Long> entry : activityViewCounts.entrySet()) {
            Long courseId = entry.getKey();
            Long activityViewCount = entry.getValue();
            long syncedViewCount = syncedViewCounts.getOrDefault(courseId, 0L);
            long delta = activityViewCount - syncedViewCount;

            try {
                // delta만 DB 반영
                transactionService.syncViewCount(
                        date,
                        courseId,
                        activityViewCount,
                        delta
                );
                counts.successCount++;
            } catch (Exception exception) {
                counts.failedCount++;
                log.atWarn().addKeyValue("event", "course.view_count.item.failed")
                        .addKeyValue("date", date).addKeyValue("courseId", courseId)
                        .addKeyValue("failureType", SafeLogDetails.failureType(exception))
                        .addKeyValue("exceptionType", exception.getClass().getName())
                        .log("Course view count item synchronization failed.");
            }
        }
    }

    // 실제 코스 처리 건수와 날짜 조회 실패 단위의 분리
    public record SyncResult(long processedCount, long successCount, long failedCount,
                             int completedDateCount, int failedDateCount) {
        public boolean hasFailures() {
            return failedCount > 0 || failedDateCount > 0;
        }

        public boolean allFailed() {
            return hasFailures() && successCount == 0
                    && (processedCount > 0 || completedDateCount == 0);
        }
    }

    private static class ItemCounts {
        private long successCount;
        private long failedCount;
    }
}

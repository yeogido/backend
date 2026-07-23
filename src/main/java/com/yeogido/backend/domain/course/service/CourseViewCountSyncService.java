package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseViewCountSyncService {

    private static final int SYNC_WINDOW_DAYS = 7;

    private final CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;
    private final CourseViewCountSyncRepository courseViewCountSyncRepository;

    @Transactional
    public void syncRecentViewCounts() {
        LocalDate today = LocalDate.now();

        for (int i = 0; i < SYNC_WINDOW_DAYS; i++) {
            LocalDate date = today.minusDays(i);

            try {
                syncViewCounts(date);
            } catch (Exception exception) {
                log.warn("Failed to sync course view counts for date={}", date, exception);
            }
        }
    }

    void syncViewCounts(LocalDate date) {
        Map<Long, Long> activityViewCounts = courseViewCountSyncRedisRepository.getActivityViewCounts(date);
        if (activityViewCounts.isEmpty()) {
            return;
        }

        Map<Long, Long> syncedViewCounts = courseViewCountSyncRedisRepository.getSyncViewCounts(date);

        activityViewCounts.forEach((courseId, activityViewCount) -> {
            long syncedViewCount = syncedViewCounts.getOrDefault(courseId, 0L);
            long delta = activityViewCount - syncedViewCount;

            try {
                if (delta > 0) {
                    courseViewCountSyncRepository.increaseViewCount(courseId, delta);
                }

                courseViewCountSyncRedisRepository.updateSyncViewCount(date, courseId, activityViewCount);
            } catch (Exception exception) {
                log.warn(
                        "Failed to sync course view count for date={}, courseId={}",
                        date,
                        courseId,
                        exception
                );
            }
        });
    }
}

package com.yeogido.backend.domain.course.popularity.scheduler;

import com.yeogido.backend.domain.course.popularity.service.CourseViewCountSyncService;
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
        // Redis 조회수 배치 동기화
        courseViewCountSyncService.syncRecentViewCounts();
    }
}

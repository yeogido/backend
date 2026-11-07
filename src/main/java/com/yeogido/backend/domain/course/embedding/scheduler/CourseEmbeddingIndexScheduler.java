package com.yeogido.backend.domain.course.embedding.scheduler;

import com.yeogido.backend.domain.course.embedding.service.CourseEmbeddingIndexService;
import com.yeogido.backend.global.logging.ScheduledExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "app.course-search.embedding", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CourseEmbeddingIndexScheduler {

    private final CourseEmbeddingIndexService indexService;

    @Scheduled(fixedDelayString = "${app.course-search.embedding.fixed-delay-ms:60000}",
            initialDelayString = "${app.course-search.embedding.initial-delay-ms:1000}")
    public void indexCourses() {
        try (var execution = ScheduledExecution.start("course.embedding.index")) {
            try {
                int saved = indexService.reindexNextBatch();
                execution.count("successCount", saved);
                execution.complete();
            } catch (RuntimeException exception) {
                // 순환 스캔 재시도 유지 및 원문과 인증 정보 노출 방지
                execution.failed(exception);
            }
        }
    }
}

package com.yeogido.backend.domain.course.embedding.scheduler;

import com.yeogido.backend.domain.course.embedding.service.CourseEmbeddingIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "app.course-search.embedding", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CourseEmbeddingIndexScheduler {

    private final CourseEmbeddingIndexService indexService;

    @Scheduled(fixedDelayString = "${app.course-search.embedding.fixed-delay-ms:60000}",
            initialDelayString = "${app.course-search.embedding.initial-delay-ms:1000}")
    public void indexCourses() {
        try {
            indexService.reindexNextBatch();
        } catch (RuntimeException exception) {
            // No query text, credentials or provider response is written to logs.
            log.warn("Course embedding indexing failed ({}); rotating scan will retry on a later pass.",
                    exception.getClass().getSimpleName());
        }
    }
}

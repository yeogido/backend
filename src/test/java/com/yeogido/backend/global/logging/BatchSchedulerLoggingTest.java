package com.yeogido.backend.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.content.dto.TourContentSyncDTO;
import com.yeogido.backend.domain.content.scheduler.TourContentSyncScheduler;
import com.yeogido.backend.domain.content.service.TourContentSyncService;
import com.yeogido.backend.domain.course.embedding.scheduler.CourseEmbeddingIndexScheduler;
import com.yeogido.backend.domain.course.embedding.service.CourseEmbeddingIndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchSchedulerLoggingTest {
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Map<String, String> previous;

    @BeforeEach void capture() {
        previous = MDC.getCopyOfContextMap();
        MDC.clear();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach void restore() {
        root.detachAppender(logs);
        logs.stop();
        if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
    }

    @ParameterizedTest
    @CsvSource({"0,0,success", "1,0,partial_failure", "0,1,partial_failure"})
    void tourSeparatesFailedItemsAndDetailFallbacksFromOrdinarySkips(int failedItems, int detailFailures,
                                                                  String outcome) {
        var service = mock(TourContentSyncService.class);
        var result = new TourContentSyncDTO.Result(4, 1, 1, 2, LocalDateTime.now());
        when(service.synchronizeWithReport()).thenAnswer(invocation -> {
            assertThat(MDC.get("executionId")).isNotBlank();
            assertThat(MDC.get("requestId")).isNull();
            return new TourContentSyncService.SyncReport(result, failedItems, detailFailures);
        });

        new TourContentSyncScheduler(service).synchronizeTourContents();

        var summary = summary();
        assertThat(field(summary, "outcome")).isEqualTo(outcome);
        assertThat(field(summary, "processedCount")).isEqualTo(4L);
        assertThat(field(summary, "successCount")).isEqualTo(2L);
        assertThat(field(summary, "failedCount")).isEqualTo((long) failedItems);
        assertThat(field(summary, "detailFailureCount")).isEqualTo((long) detailFailures);
        assertThat(field(summary, "skippedCount")).isEqualTo(2L - failedItems);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test void tourFailurePropagatesWhileEmbeddingFailureRemainsSwallowed() {
        var failure = new IllegalStateException("Authorization: fake-private-token");
        var tour = mock(TourContentSyncService.class);
        when(tour.synchronizeWithReport()).thenThrow(failure);
        assertThatThrownBy(() -> new TourContentSyncScheduler(tour).synchronizeTourContents()).isSameAs(failure);
        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        logs.list.clear();
        var index = mock(CourseEmbeddingIndexService.class);
        when(index.reindexNextBatch()).thenThrow(failure);
        assertThatCode(() -> new CourseEmbeddingIndexScheduler(index).indexCourses()).doesNotThrowAnyException();
        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        assertThat(field(summary(), "successCount")).isNull();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs()).doesNotContain("fake-private");
        });
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test void embeddingRecordsOnlyConfirmedSavedCount() {
        var service = mock(CourseEmbeddingIndexService.class);
        when(service.reindexNextBatch()).thenReturn(2);
        new CourseEmbeddingIndexScheduler(service).indexCourses();
        assertThat(field(summary(), "successCount")).isEqualTo(2L);
        assertThat(field(summary(), "processedCount")).isNull();
        assertThat(field(summary(), "outcome")).isEqualTo("success");
        assertThat(summary().getMDCPropertyMap()).containsEntry("jobName", "course.embedding.index");
    }

    @Test void missingTourReportIsNeverReportedAsSuccess() {
        var service = mock(TourContentSyncService.class);
        new TourContentSyncScheduler(service).synchronizeTourContents();
        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        assertThat(field(summary(), "processedCount")).isNull();
        logs.list.clear();
        when(service.synchronizeWithReport()).thenReturn(new TourContentSyncService.SyncReport(null, 0, 0));
        new TourContentSyncScheduler(service).synchronizeTourContents();
        assertThat(field(summary(), "outcome")).isEqualTo("failed");
    }

    private ILoggingEvent summary() {
        var summaries = logs.list.stream().filter(event ->
                "scheduler.execution.completed".equals(field(event, "event"))).toList();
        assertThat(summaries).hasSize(1);
        return summaries.getFirst();
    }

    private Object field(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) return null;
        return event.getKeyValuePairs().stream().filter(pair -> key.equals(pair.key))
                .map(pair -> pair.value).findFirst().orElse(null);
    }
}

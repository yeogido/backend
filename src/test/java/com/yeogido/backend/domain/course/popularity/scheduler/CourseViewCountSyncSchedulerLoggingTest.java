package com.yeogido.backend.domain.course.popularity.scheduler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.course.popularity.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.domain.course.popularity.service.CourseViewCountSyncService;
import com.yeogido.backend.domain.course.popularity.service.CourseViewCountSyncTransactionService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseViewCountSyncSchedulerLoggingTest {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Map<String, String> previousContext;

    @BeforeEach
    void capture() {
        previousContext = MDC.getCopyOfContextMap();
        MDC.clear();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void restore() {
        root.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear();
        else MDC.setContextMap(previousContext);
    }

    @Test
    void missingReportIsNeverReportedAsSuccess() {
        var service = mock(CourseViewCountSyncService.class);
        new CourseViewCountSyncScheduler(service).syncCourseViewCounts();
        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        assertThat(field(summary(), "processedCount")).isNull();
    }

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of(new CourseViewCountSyncService.SyncResult(2, 2, 0, 7, 0), "success"),
                Arguments.of(new CourseViewCountSyncService.SyncResult(2, 1, 1, 7, 0), "partial_failure"),
                Arguments.of(new CourseViewCountSyncService.SyncResult(2, 0, 2, 7, 0), "failed"),
                Arguments.of(new CourseViewCountSyncService.SyncResult(0, 0, 0, 6, 1), "partial_failure"),
                Arguments.of(new CourseViewCountSyncService.SyncResult(0, 0, 0, 0, 7), "failed"));
    }

    @ParameterizedTest
    @MethodSource("outcomes")
    void reportsActualItemCountsSeparatelyFromDateFailures(CourseViewCountSyncService.SyncResult result,
                                                          String outcome) {
        var service = mock(CourseViewCountSyncService.class);
        when(service.syncRecentViewCounts()).thenReturn(result);

        new CourseViewCountSyncScheduler(service).syncCourseViewCounts();

        var summary = summary();
        assertThat(field(summary, "outcome")).isEqualTo(outcome);
        assertThat(field(summary, "processedCount")).isEqualTo(result.processedCount());
        assertThat(field(summary, "successCount")).isEqualTo(result.successCount());
        assertThat(field(summary, "failedCount")).isEqualTo(result.failedCount());
        assertThat(field(summary, "failedDateCount")).isEqualTo((long) result.failedDateCount());
        assertThat(field(summary, "completedDateCount")).isEqualTo((long) result.completedDateCount());
        assertThat(((Number) field(summary, "durationMs")).longValue()).isGreaterThanOrEqualTo(0);
        assertThat(summary.getMDCPropertyMap()).containsEntry("jobName", "course.view_count.sync")
                .doesNotContainKey("requestId");
        assertThat(UUID.fromString(summary.getMDCPropertyMap().get("executionId"))).isNotNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void itemFailureAndSummaryShareExecutionIdAndRestorePreviousHttpContext() {
        var redis = mock(CourseViewCountSyncRedisRepository.class);
        var transactions = mock(CourseViewCountSyncTransactionService.class);
        var service = new CourseViewCountSyncService(redis, transactions);
        LocalDate today = LocalDate.now();
        when(redis.getActivityViewCounts(any())).thenReturn(Map.of());
        when(redis.getActivityViewCounts(today)).thenReturn(Map.of(1L, 3L, 2L, 4L));
        when(redis.getSyncViewCounts(today)).thenReturn(Map.of());
        doThrow(new IllegalStateException("Authorization: Bearer fake-private-token"))
                .when(transactions).syncViewCount(eq(today), eq(1L), eq(3L), eq(3L));
        Map<String, String> outer = Map.of("requestId", "outer-request", "executionId", "outer-execution",
                "jobName", "outer-job", "other", "preserved");
        MDC.setContextMap(outer);

        new CourseViewCountSyncScheduler(service).syncCourseViewCounts();

        var summary = summary();
        var item = logs.list.stream()
                .filter(event -> "course.view_count.item.failed".equals(field(event, "event")))
                .findFirst().orElseThrow();
        assertThat(item.getMDCPropertyMap().get("executionId"))
                .isEqualTo(summary.getMDCPropertyMap().get("executionId")).isNotEqualTo("outer-execution");
        assertThat(field(item, "courseId")).isEqualTo(1L);
        assertThat(field(summary, "processedCount")).isEqualTo(2L);
        assertThat(field(summary, "successCount")).isEqualTo(1L);
        assertThat(field(summary, "failedCount")).isEqualTo(1L);
        assertThat(field(summary, "outcome")).isEqualTo("partial_failure");
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                    .doesNotContain("fake-private-token", "Authorization:");
            assertThat(event.getMDCPropertyMap()).doesNotContainKey("requestId");
        });
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(outer);
    }

    @Test
    void repeatedExecutionsGenerateNewIdsWhileServiceLogsKeepTheirExecutionId() {
        var service = mock(CourseViewCountSyncService.class);
        doAnswer(invocation -> {
            LoggerFactory.getLogger(getClass()).info("View count test operation");
            return new CourseViewCountSyncService.SyncResult(0, 0, 0, 7, 0);
        }).when(service).syncRecentViewCounts();
        var scheduler = new CourseViewCountSyncScheduler(service);

        scheduler.syncCourseViewCounts();
        scheduler.syncCourseViewCounts();

        var summaries = logs.list.stream()
                .filter(event -> "scheduler.execution.completed".equals(field(event, "event"))).toList();
        assertThat(summaries).hasSize(2);
        assertThat(summaries.getFirst().getMDCPropertyMap().get("executionId"))
                .isNotEqualTo(summaries.getLast().getMDCPropertyMap().get("executionId"));
        var serviceLogs = logs.list.stream()
                .filter(event -> "View count test operation".equals(event.getFormattedMessage())).toList();
        assertThat(serviceLogs).hasSize(2);
        for (int i = 0; i < 2; i++) {
            assertThat(serviceLogs.get(i).getMDCPropertyMap().get("executionId"))
                    .isEqualTo(summaries.get(i).getMDCPropertyMap().get("executionId"));
        }
    }

    @Test
    void unexpectedFailureIsRethrownAndContextIsRestored() {
        var service = mock(CourseViewCountSyncService.class);
        var failure = new IllegalStateException("fake-private-credential");
        when(service.syncRecentViewCounts()).thenThrow(failure);
        Map<String, String> outer = Map.of("requestId", "outer-request");
        MDC.setContextMap(outer);

        assertThatThrownBy(() -> new CourseViewCountSyncScheduler(service).syncCourseViewCounts())
                .isSameAs(failure);

        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(outer);
        assertThat(logs.list).allSatisfy(event -> assertThat(event.getFormattedMessage()
                + event.getKeyValuePairs()).doesNotContain("fake-private-credential"));
    }

    private ILoggingEvent summary() {
        var summaries = logs.list.stream()
                .filter(event -> "scheduler.execution.completed".equals(field(event, "event"))).toList();
        assertThat(summaries).hasSize(1);
        return summaries.getFirst();
    }

    private Object field(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) return null;
        return event.getKeyValuePairs().stream().filter(pair -> key.equals(pair.key))
                .map(pair -> pair.value).findFirst().orElse(null);
    }
}

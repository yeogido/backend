package com.yeogido.backend.domain.placeenrichment.scheduler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.course.popularity.scheduler.CoursePopularityRankingScheduler;
import com.yeogido.backend.domain.course.popularity.service.CoursePopularityRankingService;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewEnrichmentService;
import com.yeogido.backend.domain.region.popularity.scheduler.RegionPopularityRankingScheduler;
import com.yeogido.backend.domain.region.popularity.service.RegionPopularityRankingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerExecutionLoggingTest {

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
    void captureLogs() {
        previousContext = MDC.getCopyOfContextMap();
        MDC.clear();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void restoreContext() {
        root.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear(); else MDC.setContextMap(previousContext);
    }

    @ParameterizedTest
    @CsvSource({"false,0,success", "false,1,partial_failure", "false,2,failed",
            "true,0,success", "true,1,partial_failure", "true,2,failed"})
    void placeJobsCountCompletedCallsAndKeepItemLogsInTheExecution(boolean retry, int failures,
                                                                  String outcome) {
        var places = mock(PlaceRepository.class);
        var enrichment = mock(PlaceOverviewEnrichmentService.class);
        if (retry) {
            when(places.findOverviewRetryCandidates(any(), any(), eq(2), any()))
                    .thenReturn(List.of(10L, 20L));
        } else {
            when(places.findOverviewPendingCandidates(eq(2), any())).thenReturn(List.of(10L, 20L));
        }
        AtomicReference<String> invocationId = new AtomicReference<>();
        doAnswer(invocation -> {
            String executionId = MDC.get("executionId");
            if (invocationId.get() == null) invocationId.set(executionId);
            assertThat(executionId).isEqualTo(invocationId.get());
            assertThat(MDC.get("jobName")).isEqualTo(retry ? "placeOverviewRetry" : "placeOverviewPending");
            assertThat(MDC.get("requestId")).isNull();
            if (((long) invocation.getArgument(0) == 10L && failures > 0) || failures == 2) {
                throw new IllegalStateException("fake-private-api-key fake-personal-data");
            }
            return null;
        }).when(enrichment).enrich(anyLong());

        runPlaceJob(retry, places, enrichment);

        var summary = summary();
        assertThat(field(summary, "outcome")).isEqualTo(outcome);
        assertCount(summary, "processedCount", 2);
        assertCount(summary, "successCount", 2 - failures);
        assertCount(summary, "failedCount", failures);
        assertThat(summary.getMDCPropertyMap()).containsEntry("executionId", invocationId.get());
        assertThat(UUID.fromString(invocationId.get()).toString()).isEqualTo(invocationId.get());
        var itemLogs = logs.list.stream().filter(event ->
                "place.overview.processing.failed".equals(field(event, "event"))).toList();
        assertThat(itemLogs).hasSize(failures).allSatisfy(event -> {
            assertThat(event.getMDCPropertyMap()).containsEntry("executionId", invocationId.get());
            assertThat(field(event, "placeId")).isIn(10L, 20L);
        });
        assertSafe();
        verify(enrichment, times(1)).enrich(10L);
        verify(enrichment, times(1)).enrich(20L);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyPlaceBatchCompletesWithZeroCalls(boolean retry) {
        var places = mock(PlaceRepository.class);
        if (retry) {
            when(places.findOverviewRetryCandidates(any(), any(), eq(2), any())).thenReturn(List.of());
        } else {
            when(places.findOverviewPendingCandidates(eq(2), any())).thenReturn(List.of());
        }

        runPlaceJob(retry, places, mock(PlaceOverviewEnrichmentService.class));

        assertThat(field(summary(), "outcome")).isEqualTo("success");
        assertCount(summary(), "processedCount", 0);
        assertCount(summary(), "successCount", 0);
        assertCount(summary(), "failedCount", 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void candidateLookupFailurePropagatesAndRestoresPreviousMdc(boolean retry) {
        var places = mock(PlaceRepository.class);
        var failure = new IllegalStateException("fake-private-database-url");
        if (retry) {
            when(places.findOverviewRetryCandidates(any(), any(), eq(2), any())).thenThrow(failure);
        } else {
            when(places.findOverviewPendingCandidates(eq(2), any())).thenThrow(failure);
        }
        Map<String, String> outer = Map.of("executionId", "outer-execution", "jobName", "outer-job");
        MDC.setContextMap(outer);

        assertThatThrownBy(() -> runPlaceJob(retry, places, mock(PlaceOverviewEnrichmentService.class)))
                .isSameAs(failure);

        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        assertThat(field(summary(), "processedCount")).isNull();
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(outer);
        assertSafe();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rankingJobsHaveFreshIdsWithoutInventedCountsAndPreserveExceptions(boolean region) {
        var courseService = mock(CoursePopularityRankingService.class);
        var regionService = mock(RegionPopularityRankingService.class);
        Runnable run = region
                ? new RegionPopularityRankingScheduler(regionService)::refreshPopularityRankings
                : new CoursePopularityRankingScheduler(courseService)::refreshPopularityRankings;
        var answer = (org.mockito.stubbing.Answer<Void>) invocation -> {
            assertThat(MDC.get("executionId")).isNotBlank();
            assertThat(MDC.get("jobName")).isEqualTo(region ? "regionPopularityRanking" : "coursePopularityRanking");
            assertThat(MDC.get("requestId")).isNull();
            return null;
        };
        if (region) doAnswer(answer).when(regionService).refreshPopularityRankings();
        else doAnswer(answer).when(courseService).refreshPopularityRankings();

        run.run();
        String firstId = summary().getMDCPropertyMap().get("executionId");
        assertThat(field(summary(), "outcome")).isEqualTo("success");
        assertThat(field(summary(), "processedCount")).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        logs.list.clear();
        var failure = new IllegalArgumentException("fake-private-redis-password");
        if (region) doThrow(failure).when(regionService).refreshPopularityRankings();
        else doThrow(failure).when(courseService).refreshPopularityRankings();

        assertThatThrownBy(run::run).isSameAs(failure);

        assertThat(field(summary(), "outcome")).isEqualTo("failed");
        assertThat(summary().getMDCPropertyMap().get("executionId")).isNotEqualTo(firstId);
        assertThat(summary().getMDCPropertyMap()).containsEntry("jobName",
                region ? "regionPopularityRanking" : "coursePopularityRanking");
        assertThat(((Number) field(summary(), "durationMs")).longValue()).isGreaterThanOrEqualTo(0);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        assertSafe();
    }

    private void runPlaceJob(boolean retry, PlaceRepository places, PlaceOverviewEnrichmentService enrichment) {
        if (retry) new PlaceOverviewRetryScheduler(places, enrichment).retryFailedOverviews();
        else new PlaceOverviewPendingScheduler(places, enrichment).enrichPendingOverviews();
    }

    private ILoggingEvent summary() {
        var summaries = logs.list.stream().filter(event ->
                "scheduler.execution.completed".equals(field(event, "event"))).toList();
        assertThat(summaries).hasSize(1);
        return summaries.getFirst();
    }

    private void assertCount(ILoggingEvent event, String key, long value) {
        assertThat(((Number) field(event, key)).longValue()).isEqualTo(value);
    }

    private Object field(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) return null;
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value).findFirst().orElse(null);
    }

    private void assertSafe() {
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs()).doesNotContain("fake-private");
        });
    }
}

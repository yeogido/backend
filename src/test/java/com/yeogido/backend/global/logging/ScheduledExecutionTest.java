package com.yeogido.backend.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledExecutionTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ScheduledExecution.class);
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
        logger.addAppender(logs);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear();
        else MDC.setContextMap(previousContext);
    }

    @Test
    void generatesNewIdsAndRestoresCallerContextWithoutRequestIdInsideExecution() {
        var caller = Map.of("requestId", "fake-private-request", "executionId", "outer-id",
                "jobName", "outer-job", "existing", "context");
        MDC.setContextMap(caller);
        String firstId;
        try (var execution = ScheduledExecution.start("firstJob")) {
            firstId = MDC.get("executionId");
            assertThat(UUID.fromString(firstId).toString()).isEqualTo(firstId);
            assertThat(MDC.get("requestId")).isNull();
            assertThat(MDC.get("jobName")).isEqualTo("firstJob");
            logger.info("Inside scheduled work");
            execution.complete();
        }
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(caller);
        assertThat(logs.list).hasSize(2).allSatisfy(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry("executionId", firstId)
                        .doesNotContainKey("requestId"));
        try (var execution = ScheduledExecution.start("secondJob")) {
            assertThat(MDC.get("executionId")).isNotEqualTo(firstId);
            execution.complete();
        }
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(caller);
    }

    @Test
    void failureRestoresContextAndDoesNotLogOriginalException() {
        MDC.put("existing", "context");
        var failure = new IllegalStateException("Authorization: Bearer fake-private-token",
                new RuntimeException("https://provider.test?serviceKey=fake-private-key"));

        assertThatThrownBy(() -> {
            try (var execution = ScheduledExecution.start("failingJob")) {
                MDC.put("temporary", "value");
                throw failure;
            }
        }).isSameAs(failure);

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("existing", "context"));
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(field(event, "outcome")).isEqualTo("failed");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs()).doesNotContain("fake-private");
        });
    }

    @Test
    void outcomesReflectActualCountsAndUnknownCountsAreOmitted() {
        try (var execution = ScheduledExecution.start("emptyJob")) {
            execution.complete();
        }
        try (var execution = ScheduledExecution.start("mixedJob")) {
            execution.counts(3, 2, 1).complete();
        }
        try (var execution = ScheduledExecution.start("failedJob")) {
            execution.counts(2, 0, 2).complete();
        }

        assertThat(field(logs.list.get(0), "outcome")).isEqualTo("success");
        assertThat(field(logs.list.get(0), "processedCount")).isNull();
        assertThat(field(logs.list.get(1), "outcome")).isEqualTo("partial_failure");
        assertThat(field(logs.list.get(1), "processedCount")).isEqualTo(3L);
        assertThat(field(logs.list.get(1), "successCount")).isEqualTo(2L);
        assertThat(field(logs.list.get(1), "failedCount")).isEqualTo(1L);
        assertThat(field(logs.list.get(2), "outcome")).isEqualTo("failed");
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(field(event, "event")).isEqualTo("scheduler.execution.completed");
            assertThat((Long) field(event, "durationMs")).isNotNegative();
        });
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void nestedExecutionRestoresOuterExecutionAndCloseIsIdempotent() {
        try (var outer = ScheduledExecution.start("outer")) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            var inner = ScheduledExecution.start("inner");
            inner.complete();
            inner.close();
            inner.close();
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(context);
            outer.complete();
        }
        assertThat(logs.list).hasSize(2);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void concurrentExecutionsKeepTheirOwnIdsAndRestoreTheirOwnThreads() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentExecution("first", barrier));
            var second = executor.submit(() -> concurrentExecution("second", barrier));
            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(logs.list).hasSize(2).allSatisfy(event ->
                assertThat(event.getMDCPropertyMap()).doesNotContainKey("requestId"));
    }

    private String concurrentExecution(String job, CyclicBarrier barrier) throws Exception {
        var caller = Map.of("requestId", "caller-" + job, "worker", job);
        MDC.setContextMap(caller);
        try {
            String id;
            try (var execution = ScheduledExecution.start(job)) {
                id = MDC.get("executionId");
                barrier.await(5, TimeUnit.SECONDS);
                assertThat(MDC.get("executionId")).isEqualTo(id);
                assertThat(MDC.get("jobName")).isEqualTo(job);
                execution.complete();
            }
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(caller);
            return id;
        } finally {
            MDC.clear();
        }
    }

    private Object field(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value).findFirst().orElse(null);
    }
}

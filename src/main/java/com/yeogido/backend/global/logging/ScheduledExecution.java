package com.yeogido.backend.global.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ScheduledExecution implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScheduledExecution.class);
    private final Map<String, String> previousContext;
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final long startedAt = System.nanoTime();
    private String outcome = "failed";
    private String exceptionType;
    private String errorCode;
    private boolean closed;

    private ScheduledExecution(String jobName) {
        previousContext = MDC.getCopyOfContextMap();
        // HTTP 요청 문맥과 구분되는 독립 실행 문맥 설정
        MDC.remove(RequestIdFilter.MDC_KEY);
        MDC.put("executionId", UUID.randomUUID().toString());
        MDC.put("jobName", jobName);
    }

    public static ScheduledExecution start(String jobName) {
        return new ScheduledExecution(jobName);
    }

    public ScheduledExecution counts(long processedCount, long successCount, long failedCount) {
        return count("processedCount", processedCount)
                .count("successCount", successCount)
                .count("failedCount", failedCount);
    }

    public ScheduledExecution count(String field, long value) {
        counts.put(field, value);
        return this;
    }

    public void complete() {
        long failedCount = counts.getOrDefault("failedCount", 0L);
        long successCount = counts.getOrDefault("successCount", 0L);
        long processedCount = counts.getOrDefault("processedCount", 0L);
        outcome = failedCount == 0 ? "success"
                : successCount > 0 || processedCount > failedCount ? "partial_failure" : "failed";
    }

    public void completePartialFailure() {
        outcome = "partial_failure";
    }

    public void completeFailed() {
        outcome = "failed";
    }

    public void failed(Throwable failure) {
        outcome = "failed";
        exceptionType = failure.getClass().getName();
        if (failure instanceof com.yeogido.backend.global.exception.GeneralException serviceFailure) {
            errorCode = serviceFailure.getErrorCode().getCode();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            var event = switch (outcome) {
                case "success" -> log.atInfo();
                case "partial_failure" -> log.atWarn();
                default -> log.atError();
            };
            event.addKeyValue("event", "scheduler.execution.completed")
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            counts.forEach(event::addKeyValue);
            if (exceptionType != null) event.addKeyValue("exceptionType", exceptionType);
            if (errorCode != null) event.addKeyValue("errorCode", errorCode);
            event.log("Scheduled execution completed.");
        } catch (RuntimeException ignored) {
            // 집계 로그 실패에 의한 작업 결과 변경 방지
        } finally {
            // 정상 종료와 예외 종료 모두 호출 전 전체 문맥 복원
            if (previousContext == null) MDC.clear();
            else MDC.setContextMap(previousContext);
        }
    }
}

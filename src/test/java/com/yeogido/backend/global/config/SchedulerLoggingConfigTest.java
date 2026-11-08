package com.yeogido.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.global.logging.SafeSchedulerErrorHandler;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulerLoggingConfigTest {

    @Test
    void bootSchedulerSuppressesUnsafeExceptionsAndContinuesRepeatingTask() {
        Logger logger = (Logger) LoggerFactory.getLogger(SafeSchedulerErrorHandler.class);
        CountDownLatch loggedFailures = new CountDownLatch(2);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected synchronized void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
                loggedFailures.countDown();
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                    .withUserConfiguration(SchedulingFixture.class, SchedulerLoggingConfig.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
                        ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
                        var task = scheduler.scheduleAtFixedRate(() -> {
                            throw new IllegalStateException("serviceKey=secret-token",
                                    new IllegalArgumentException("private-response-body"));
                        }, Duration.ofMillis(20));
                        try {
                            assertThat(loggedFailures.await(5, TimeUnit.SECONDS)).isTrue();
                            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                        } finally {
                            task.cancel(false);
                        }
                    });
            assertThat(appender.list).hasSizeGreaterThanOrEqualTo(2);
            for (ILoggingEvent event : appender.list) {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage())
                        .doesNotContain("secret-token", "private-response-body");
                assertThat(event.getKeyValuePairs().toString())
                        .contains("scheduler.task.failed", "exceptionType", "IllegalStateException", "stackFrames")
                        .doesNotContain("secret-token", "private-response-body");
                assertThat(event.getKeyValuePairs()).filteredOn(pair -> pair.key.equals("exceptionType"))
                        .singleElement().satisfies(pair ->
                                assertThat(pair.value).isEqualTo("java.lang.IllegalStateException"));
                assertThat(event.getMDCPropertyMap()).doesNotContainKeys("executionId", "requestId");
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void diagnosticFailureCannotCancelRepeatingTask() {
        RuntimeException failure = mock(RuntimeException.class);
        when(failure.getStackTrace()).thenThrow(new IllegalStateException("logging failure"));

        assertThatCode(() -> new SafeSchedulerErrorHandler().handleError(failure))
                .doesNotThrowAnyException();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingFixture {
    }
}

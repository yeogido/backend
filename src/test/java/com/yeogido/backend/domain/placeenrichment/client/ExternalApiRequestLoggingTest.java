package com.yeogido.backend.domain.placeenrichment.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.global.exception.GlobalExceptionHandler;
import com.yeogido.backend.global.logging.RequestIdFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExternalApiRequestLoggingTest {

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    void countsOneUpstreamFailureAndOneFinalRequestOutcomeWithTheSameRequestId(int upstreamStatus)
            throws Exception {
        var builder = RestClient.builder().baseUrl("https://provider.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(upstreamStatus))
                .contentType(MediaType.APPLICATION_JSON).body("{\"token\":\"fake-private-token\"}"));
        var client = new GeminiSearchClient(builder.build(), "fake-api-key", "model");
        var mvc = MockMvcBuilders.standaloneSetup(new ProbeController(client))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter()).build();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.clear();
        appender.start();
        root.addAppender(appender);
        try {
            var result = mvc.perform(get("/upstream-failure"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("isSuccess").value(false))
                    .andExpect(jsonPath("code").value("PLACE_ENRICHMENT5022"))
                    .andReturn();
            String requestId = result.getResponse().getHeader("X-Request-ID");
            var failures = appender.list.stream()
                    .filter(event -> hasEvent(event, "external_api.call.failed")).toList();
            var outcomes = appender.list.stream()
                    .filter(event -> hasEvent(event, "api.request.failed")).toList();
            assertThat(failures).hasSize(1);
            assertThat(outcomes).hasSize(1);
            assertThat(requestId).isNotBlank();
            assertThat(failures.getFirst().getMDCPropertyMap()).containsEntry("requestId", requestId);
            assertThat(outcomes.getFirst().getMDCPropertyMap()).containsEntry("requestId", requestId);
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("fake-private-token", "fake-api-key",
                        "fake-search", "fake-prompt");
                assertThat(String.valueOf(event.getKeyValuePairs())).doesNotContain("fake-private-token",
                        "fake-api-key", "fake-search", "fake-prompt");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
            server.verify();
        } finally {
            root.detachAppender(appender);
            appender.stop();
            if (previous == null) MDC.clear();
            else MDC.setContextMap(previous);
        }
    }

    private boolean hasEvent(ILoggingEvent event, String name) {
        return event.getKeyValuePairs() != null && event.getKeyValuePairs().stream()
                .anyMatch(pair -> pair.key.equals("event") && pair.value.equals(name));
    }

    @RestController
    static class ProbeController {
        private final GeminiSearchClient client;

        ProbeController(GeminiSearchClient client) {
            this.client = client;
        }

        @GetMapping("/upstream-failure")
        Object execute() {
            return client.search("fake-prompt", "fake-search");
        }
    }
}

package com.yeogido.backend.domain.placeenrichment.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DaumSearchClientLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(DaumSearchClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private MockRestServiceServer server;
    private DaumSearchClient client;
    private Map<String, String> previousContext;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DaumSearchClient(builder.build(), "fake-api-key", 5);
        previousContext = MDC.getCopyOfContextMap();
        MDC.put("requestId", "external-request");
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear();
        else MDC.setContextMap(previousContext);
    }

    @ParameterizedTest
    @CsvSource({"401,authentication_failure,false", "403,permission_or_access_failure,false",
            "429,quota_or_rate_limit,true", "400,request_error,false", "503,provider_server_error,true"})
    void httpFailureHasOneSafeStructuredEvent(int status, String failureType, boolean retryable) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("private-response fake-api-key private-access-token"));

        assertFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);

        assertThat(fields()).containsEntry("failureType", failureType)
                .containsEntry("upstreamHttpStatus", status).containsEntry("retryable", retryable);
        server.verify();
    }

    @Test
    void timeoutDoesNotExposeSensitiveUrlOrExceptionMessage() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException(
                "https://example.test?key=fake-api-key&query=private-search-query private-access-token")));

        assertFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);

        assertThat(fields()).containsEntry("failureType", "timeout").containsEntry("retryable", true)
                .doesNotContainKey("upstreamHttpStatus");
        server.verify();
    }

    @Test
    void malformedResponseIsLoggedOnceWithoutRawPayload() {
        server.expect(anything()).andRespond(withSuccess("{\"documents\":null}", MediaType.APPLICATION_JSON));

        assertFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);

        assertThat(fields()).containsEntry("failureType", "response_parse_error")
                .containsEntry("errorCode", PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR.getCode())
                .containsEntry("retryable", false);
        server.verify();
    }

    @Test
    void laterWebFailureIsOneCallFailureWithCorrectOperation() {
        server.expect(anything()).andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getKeyValuePairs()).anySatisfy(pair -> {
            assertThat(pair.key).isEqualTo("operation");
            assertThat(pair.value).isEqualTo("searchWeb");
        });
        server.verify();
    }

    @Test
    void missingKeyDoesNotCountAsAnUpstreamFailure() {
        client = new DaumSearchClient(RestClient.create(), "", 5);
        assertFailure(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
        assertThat(logs.list).isEmpty();
    }

    private void assertFailure(PlaceEnrichmentErrorCode expectedCode) {
        assertThatThrownBy(() -> client.search(new com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest.Input("1", "private-search-query", null, null, "private-address", null, null, null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(expectedCode)).hasNoCause();
    }

    private Map<String, Object> fields() {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.getFirst();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "external-request");
        assertThat(event.getThrowableProxy()).isNull();
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "external_api.call.failed")
                .containsEntry("provider", "daum").containsEntry("operation", "searchBlog");
        assertThat(event.getFormattedMessage() + fields).doesNotContain("fake-api-key", "private-response",
                "private-access-token", "private-search-query", "private-address", "https://example.test");
        return fields;
    }
}

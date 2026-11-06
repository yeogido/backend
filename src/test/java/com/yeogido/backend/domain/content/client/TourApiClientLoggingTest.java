package com.yeogido.backend.domain.content.client;

import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.global.logging.SafeLogDetails;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class TourApiClientLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(TourApiClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Map<String, String> previousContext;
    private TourApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        previousContext = MDC.getCopyOfContextMap();
        MDC.put("requestId", "tour-request-id");
        logs.start();
        logger.addAppender(logs);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TourApiClient(builder);
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.com/KorService2");
        ReflectionTestUtils.setField(client, "serviceKey", "secret-service-key");
        ReflectionTestUtils.setField(client, "mobileOs", "ETC");
        ReflectionTestUtils.setField(client, "mobileApp", "Yeogido");
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear(); else MDC.setContextMap(previousContext);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    void httpFailureKeepsStatusButNotResponseBodyOrRequestUrl(int status, CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("Authorization: Bearer secret-access-token; Cookie: secret-cookie; secret-provider-body"));

        assertApiError();

        assertThat(fields()).containsEntry("failureType", SafeLogDetails.httpFailureType(status))
                .containsEntry("upstreamHttpStatus", status)
                .containsEntry("retryable", status == 429 || status == 503);
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void timeoutDoesNotExposeUrlMessageCauseOrSuppressedException(CapturedOutput output) {
        SocketTimeoutException failure = new SocketTimeoutException("secret-timeout-message");
        failure.initCause(new IOException("secret-nested-cause"));
        failure.addSuppressed(new IOException("secret-suppressed"));
        server.expect(anything()).andRespond(withException(failure));

        assertApiError();

        assertThat(fields()).containsEntry("failureType", "timeout").containsEntry("retryable", true);
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void connectionFailureKeepsClassificationWithoutUrl(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new IOException("secret-connection-message")));

        assertApiError();

        assertThat(fields()).containsEntry("failureType", "connection_error");
        assertNoSecrets(output);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret-service-key", "9999", "", "null"})
    void invalidResponseLogsOnlyBoundedResultCode(String code, CapturedOutput output) {
        String header = "null".equals(code) ? "{}" : "{\"resultCode\":\"" + code + "\"}";
        server.expect(anything()).andRespond(withSuccess(
                "{\"response\":{\"header\":" + header + ",\"body\":{\"private\":\"secret-provider-body\"}}}",
                MediaType.APPLICATION_JSON));

        assertApiError();

        String expected = "9999".equals(code) ? "9999"
                : code.isEmpty() || "null".equals(code) ? "missing" : "invalid";
        assertThat(fields()).containsEntry("failureType", "invalid_response").containsEntry("resultCode", expected);
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void malformedResponseDoesNotExposeParserInput(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{secret-provider-body", MediaType.APPLICATION_JSON));

        assertApiError();

        assertThat(fields()).containsEntry("failureType", "response_parse_error");
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void successfulResponseIsUnchangedAndNotLogged(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess(
                "{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"private\":\"secret-provider-body\"}}}",
                MediaType.APPLICATION_JSON));

        assertThat(client.detailCommon("secret-content-id").path("response").path("body").path("private").asText())
                .isEqualTo("secret-provider-body");
        assertThat(output.getAll()).doesNotContain("Tour API");
        assertNoSecrets(output);
        server.verify();
    }

    private Map<String, Object> fields() {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.getFirst();
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "tour-request-id");
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "external_api.call.failed")
                .containsEntry("provider", "tour").containsEntry("operation", "/detailCommon2")
                .containsEntry("errorCode", ContentErrorCode.TOUR_API_ERROR.getCode());
        assertThat(fields.toString()).doesNotContain("secret-", "serviceKey=", "Bearer ", "Cookie:");
        return fields;
    }

    private void assertApiError() {
        assertThatThrownBy(() -> client.detailCommon("secret-content-id"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ContentErrorCode.TOUR_API_ERROR))
                .hasNoCause();
    }

    private void assertNoSecrets(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain("secret-", "https://example.com", "serviceKey=", "Bearer ", "Cookie:");
    }
}

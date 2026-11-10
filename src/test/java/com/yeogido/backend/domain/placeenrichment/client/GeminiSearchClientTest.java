package com.yeogido.backend.domain.placeenrichment.client;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import com.yeogido.backend.global.exception.GeneralException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class GeminiSearchClientTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
        "400|{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"API_KEY_INVALID\"}]}}|AUTHENTICATION",
        "400|{\"error\":{\"status\":\"FAILED_PRECONDITION\"}}|CONFIGURATION",
        "402|{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}|CONFIGURATION",
        "400|{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}|REQUEST",
        "400|{\"error\":{\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}|REQUEST",
        "400|{broken-json|REQUEST",
        "503|{\"error\":{\"status\":\"FAILED_PRECONDITION\"}}|SERVER"
    })
    void classifiesStructuredAccountFailuresWithoutRetainingProviderBody(int status, String body,
            ExternalApiFailureKind kind, org.springframework.boot.test.system.CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .header("Retry-After", "90").body(body));
        assertThatThrownBy(() -> new GeminiSearchClient(builder.build(), "secret-key", "model").search("a", "b"))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(kind);
                    assertThat(failure.getUpstreamHttpStatus()).isEqualTo(status);
                    assertThat(failure.getRetryAfter()).isEqualTo(java.time.Duration.ofSeconds(90));
                    assertThat(failure.getProvider()).isEqualTo("gemini");
                    assertThat(failure.getOperation()).isEqualTo("generateContent");
                }).hasNoCause().hasMessageNotContaining(body);
        assertThat(output.getOut()).doesNotContain(body, "secret-key");
        server.verify();
    }

    @Test
    void oversizedErrorBodyKeepsHttpClassification() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"status\":\"FAILED_PRECONDITION\"},\"padding\":\"" + "x".repeat(65536) + "\"}"));
        assertThatThrownBy(() -> new GeminiSearchClient(builder.build(), "secret", "model").search("a", "b"))
                .isInstanceOfSatisfying(ExternalApiFailure.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(ExternalApiFailureKind.REQUEST));
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "401,AUTHENTICATION", "403,AUTHENTICATION", "408,TIMEOUT", "429,RATE_LIMIT",
        "400,REQUEST", "503,SERVER"
    })
    void preservesFailureMetadataForRetryDecisions(int status, ExternalApiFailureKind kind) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .header("Retry-After", "90").body("private body"));
        assertThatThrownBy(() -> new GeminiSearchClient(builder.build(), "secret", "model").search("a", "b"))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure -> {
                    assertThat(failure.getProvider()).isEqualTo("gemini");
                    assertThat(failure.getOperation()).isEqualTo("generateContent");
                    assertThat(failure.getKind()).isEqualTo(kind);
                    assertThat(failure.getUpstreamHttpStatus()).isEqualTo(status);
                    assertThat(failure.getRetryAfter()).isEqualTo(java.time.Duration.ofSeconds(90));
                }).hasNoCause();
        server.verify();
    }

    @Test
    void retryAfterAcceptsHttpDateAndPreservesLongUpstreamDelays() {
        var now = java.time.Instant.parse("2026-11-08T00:00:00Z");
        try (var time = org.mockito.Mockito.mockStatic(java.time.Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            time.when(java.time.Instant::now).thenReturn(now);
            assertThat(ExternalApiFailure.parseRetryAfter("Sun, 8 Nov 2026 00:02:00 GMT"))
                    .isEqualTo(java.time.Duration.ofMinutes(2));
            assertThat(ExternalApiFailure.parseRetryAfter("Sun, 8 Nov 2026 00:00:00 GMT"))
                    .isEqualTo(java.time.Duration.ZERO);
        }
        assertThat(ExternalApiFailure.parseRetryAfter("172800"))
                .isEqualTo(java.time.Duration.ofHours(48));
        assertThat(new ExternalApiFailure(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR,
                "gemini", "generateContent", ExternalApiFailureKind.RATE_LIMIT, 429, null,
                java.time.Duration.ofHours(48)).getRetryAfter()).isEqualTo(java.time.Duration.ofHours(48));
        assertThat(ExternalApiFailure.parseRetryAfter("-1")).isNull();
        assertThat(ExternalApiFailure.parseRetryAfter("malformed-secret")).isNull();
        assertThat(ExternalApiFailure.parseRetryAfter("9".repeat(100))).isNull();
    }


    private final Logger logger = (Logger) LoggerFactory.getLogger(GeminiSearchClient.class);
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
        MDC.put("requestId", "gemini-request");
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void restoreLogging() {
        logger.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear();
        else MDC.setContextMap(previousContext);
    }

    private Map<String, Object> failureFields() {
        assertThat(logs.list).hasSize(1);
        var event = logs.list.getFirst();
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "gemini-request");
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "external_api.call.failed")
                .containsEntry("provider", "gemini")
                .containsEntry("operation", "generateContent")
                .containsEntry("errorCode", PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR.getCode());
        assertThat(fields.toString()).doesNotContain("secret-key", "private-provider-body",
                "private-instructions", "private-search-query", "secret-timeout-details", "broken-json");
        return fields;
    }


    @Test
    void retryTimestampPreservesFortyEightHoursAndSaturatesAtDatabaseMaximum() {
        var now = java.time.LocalDateTime.of(2026, 11, 8, 0, 0);
        var maximum = java.time.LocalDateTime.of(9999, 1, 1, 0, 0);
        assertThat(ExternalApiFailure.retryAt(now, java.time.Duration.ofHours(48)))
                .isEqualTo(now.plusDays(2));
        assertThat(ExternalApiFailure.retryAt(now, java.time.Duration.ofDays(4000000)))
                .isEqualTo(maximum);
        assertThat(ExternalApiFailure.retryAt(now, java.time.Duration.ofSeconds(Long.MAX_VALUE)))
                .isEqualTo(maximum);
        assertThat(ExternalApiFailure.retryAt(maximum.minusSeconds(1), java.time.Duration.ofSeconds(2)))
                .isEqualTo(maximum);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "401,authentication_failure", "403,permission_or_access_failure", "429,quota_or_rate_limit",
        "400,request_error", "503,provider_server_error"
    })
    void classifiesHttpFailuresWithoutLoggingSecrets(int status, String type,
            org.springframework.boot.test.system.CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("private-provider-body"));
        GeminiSearchClient client = new GeminiSearchClient(builder.build(), "secret-key", "model");
        assertThatThrownBy(() -> client.search("private-instructions", "private-search-query"))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR)).hasNoCause();
        assertThat(failureFields()).containsEntry("failureType", type)
                .containsEntry("upstreamHttpStatus", status)
                .containsEntry("retryable", status == 429 || status >= 500);
        assertThat(output.getOut()).doesNotContain("secret-key", "private-provider-body", "private-instructions", "private-search-query");
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "null", "{broken-json"})
    void invalidJsonAndEmptyResponsesRemainSanitizedApiErrors(String body,
            org.springframework.boot.test.system.CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new GeminiSearchClient(builder.build(), "secret-key", "model").search("a", "b"))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR)).hasNoCause();
        assertThat(failureFields()).containsEntry("failureType", "response_parse_error")
                .containsEntry("retryable", false).doesNotContainKey("upstreamHttpStatus");
        assertThat(output.getOut()).doesNotContain("secret-key", "broken-json");
        server.verify();
    }


    @Test
    void timeoutIsLoggedWithoutExceptionMessage(org.springframework.boot.test.system.CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("secret-timeout-details")));
        assertThatThrownBy(() -> new GeminiSearchClient(builder.build(), "secret-key", "model").search("a", "b"))
                .isInstanceOf(GeneralException.class).hasNoCause();
        assertThat(failureFields()).containsEntry("failureType", "timeout")
                .containsEntry("retryable", true).doesNotContainKey("upstreamHttpStatus");
        assertThat(output.getOut()).doesNotContain("secret-timeout-details", "secret-key");
        server.verify();
    }

    @Test
    void usesOrdinaryGenerationAndKeepsCredentialsOutOfUrl() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiSearchClient client = new GeminiSearchClient(builder.build(), "secret-key", "gemini-2.5-flash");
        server.expect(requestTo("https://example.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("instructions"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("input"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.search("instructions", "input")).isEqualTo(new ObjectMapper().createObjectNode());
        assertThat(logs.list).isEmpty();
        server.verify();
    }

    @Test
    void apiErrorsDoNotExposeProviderBodiesOrBecomeNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiSearchClient client = new GeminiSearchClient(builder.build(), "secret-key", "gemini-2.5-flash");
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret-key provider body"));

        assertThatThrownBy(() -> client.search("instructions", "input")).isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR)).hasMessageNotContaining("secret-key").hasNoCause();
        server.verify();
    }

    @Test
    void missingKeyAndInvalidModelFailBeforeRequest() {
        RestClient client = RestClient.create();
        assertThatThrownBy(() -> new GeminiSearchClient(client, "", "gemini-2.5-flash").search("a", "b"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG));
        assertThatThrownBy(() -> new GeminiSearchClient(client, "key", "../bad").search("a", "b"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG));
        assertThat(logs.list).isEmpty();
    }
}

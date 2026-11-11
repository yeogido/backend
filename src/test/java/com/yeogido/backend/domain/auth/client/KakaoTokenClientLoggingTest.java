package com.yeogido.backend.domain.auth.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.stream.Collectors;

import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
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
class KakaoTokenClientLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(KakaoTokenClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logs.stop();
        MDC.clear();
    }

    private Map<String, Object> loggedFields() {
        assertThat(logs.list).hasSize(1);
        var event = logs.list.getFirst();
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-test");
        var fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "external_api.call.failed")
                .containsEntry("provider", "kakao");
        assertThat(event.getFormattedMessage() + fields).doesNotContain("secret-", "private-", "fake-", "https://");
        return fields;
    }


    private KakaoTokenClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        MDC.put("requestId", "request-test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoTokenClient(builder);
        ReflectionTestUtils.setField(client, "tokenUri", "https://example.com/token?key=secret-url-key");
        ReflectionTestUtils.setField(client, "clientId", "secret-client-id");
        ReflectionTestUtils.setField(client, "clientSecret", "secret-client-secret");
    }

    @Test
    void serverFailureKeepsStatusWithoutResponseOrTokenData(CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("secret-access-token secret-refresh-token secret-authorization-code secret-provider-body"));

        assertError(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(loggedFields()).containsEntry("operation", "token").containsEntry("failureType", "provider_server_error")
                .containsEntry("upstreamHttpStatus", 503).containsEntry("retryable", true);
        assertNoSecrets(output);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429})
    void clientFailureKeepsExistingErrorAndLogsOneSafeFailure(int status, CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("secret-provider-body"));

        assertError(AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE);

        assertThat(loggedFields()).containsEntry("operation", "token")
                .containsEntry("upstreamHttpStatus", status)
                .containsEntry("errorCode", AuthErrorCode.INVALID_SOCIAL_AUTHORIZATION_CODE.getCode())
                .containsEntry("retryable", status == 429);
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void timeoutDoesNotExposeUrlMessageCauseOrSuppressedException(CapturedOutput output) {
        SocketTimeoutException failure = new SocketTimeoutException("secret-timeout-message");
        failure.initCause(new IOException("secret-nested-cause"));
        failure.addSuppressed(new IOException("secret-suppressed"));
        server.expect(anything()).andRespond(withException(failure));

        assertError(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(loggedFields()).containsEntry("operation", "token").containsEntry("failureType", "timeout")
                .containsEntry("retryable", true).doesNotContainKey("upstreamHttpStatus");
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void connectionFailureKeepsClassification(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new IOException("secret-connection-message")));

        assertError(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(loggedFields()).containsEntry("failureType", "connection_error");
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void malformedResponseDoesNotExposeParserInput(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{secret-provider-body", MediaType.APPLICATION_JSON));

        assertError(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(loggedFields()).containsEntry("failureType", "response_parse_error");
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void successfulTokenIsUnchangedAndNotLogged(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"access_token\":\"secret-access-token\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getAccessToken("secret-authorization-code", "https://example.com/secret-redirect"))
                .isEqualTo("secret-access-token");
        assertThat(logs.list).isEmpty();
        assertNoSecrets(output);
        server.verify();
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> client.getAccessToken("secret-authorization-code", "https://example.com/secret-redirect"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected))
                .hasNoCause();
    }

    private void assertNoSecrets(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain("secret-", "https://example.com", "Bearer ", "Cookie:");
    }
}

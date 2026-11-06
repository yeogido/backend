package com.yeogido.backend.global.logging;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class SafeLogDetailsTest {

    @ParameterizedTest
    @CsvSource({"401,authentication_failure,false", "403,permission_or_access_failure,false",
            "408,request_error,true", "429,quota_or_rate_limit,true", "400,request_error,false",
            "500,provider_server_error,true", "503,provider_server_error,true",
            "302,unexpected_http_status,false"})
    void classifiesHttpFailuresAndRetryability(int status, String failureType, boolean retryable) {
        assertThat(SafeLogDetails.httpFailureType(status)).isEqualTo(failureType);
        assertThat(SafeLogDetails.isRetryable(failureType, status)).isEqualTo(retryable);
    }

    @Test
    void identifiesTransientTransportFailuresWithoutAssumingRetriesAreExecuted() {
        assertThat(SafeLogDetails.isRetryable("timeout", null)).isTrue();
        assertThat(SafeLogDetails.isRetryable("connection_error", null)).isTrue();
        assertThat(SafeLogDetails.isRetryable("response_parse_error", null)).isFalse();
        assertThat(SafeLogDetails.isRetryable("client_error", null)).isFalse();
    }

    @Test
    void recognizesAwsSdkTimeoutsWithoutMessagesOrNestedCauses() {
        assertThat(SafeLogDetails.failureType(ApiCallTimeoutException.create(1000))).isEqualTo("timeout");
        assertThat(SafeLogDetails.failureType(ApiCallAttemptTimeoutException.create(1000))).isEqualTo("timeout");
    }

    @Test
    void classifiesNestedTimeoutWithoutReadingMessages() {
        var failure = new ResourceAccessException("secret-url", new SocketTimeoutException("secret-key"));
        assertThat(SafeLogDetails.failureType(failure)).isEqualTo("timeout");
    }

    @Test
    void classifiesConnectionAndParsingFailures() {
        assertThat(SafeLogDetails.failureType(new ResourceAccessException("secret-url")))
                .isEqualTo("connection_error");
        var parsing = new HttpMessageNotReadableException("secret-body", new MockHttpInputMessage(new byte[0]));
        assertThat(SafeLogDetails.failureType(new RestClientException("secret-body", parsing)))
                .isEqualTo("response_parse_error");
        assertThat(SafeLogDetails.failureType(new RestClientException("secret-body")))
                .isEqualTo("client_error");
    }

    @Test
    void cyclicCausesCannotHangLogging() {
        var first = new RuntimeException("first-secret");
        var second = new RuntimeException("second-secret", first);
        first.initCause(second);
        assertThat(SafeLogDetails.failureType(first)).isEqualTo("client_error");
    }

    @Test
    void stackOutputIsBoundedAndContainsOnlyCodeLocations() {
        var failure = new RuntimeException("secret-message", new RuntimeException("secret-cause"));
        failure.addSuppressed(new RuntimeException("secret-suppressed"));
        var frames = new StackTraceElement[25];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("Example", "method" + i, "Example.java", i + 1);
        }
        failure.setStackTrace(frames);

        assertThat(SafeLogDetails.stackFrames(failure)).contains("method0", "method19")
                .doesNotContain("method20", "secret-");
        assertThat(failure.getStackTrace()).hasSize(25);
    }
}

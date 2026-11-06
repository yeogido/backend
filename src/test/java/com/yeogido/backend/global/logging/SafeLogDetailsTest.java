package com.yeogido.backend.global.logging;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;

class SafeLogDetailsTest {

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

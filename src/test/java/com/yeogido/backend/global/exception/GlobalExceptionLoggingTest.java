package com.yeogido.backend.global.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedFailureKeepsResponseAndSafeStackWithoutMessages(CapturedOutput output) {
        var failure = new IllegalStateException("Authorization: Bearer fake-access-token",
                new RuntimeException("https://example.com?serviceKey=fake-api-key"));
        failure.addSuppressed(new RuntimeException("Cookie: fake-cookie; code=fake-auth-code"));

        var response = handler.handleException(failure);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("COMMON5001");
        assertThat(response.getBody().message()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.getBody().result()).isNull();
        assertThat(output.getAll()).contains("exceptionType=java.lang.IllegalStateException",
                        "errorCode=COMMON5001", "GlobalExceptionLoggingTest")
                .doesNotContain("fake-access-token", "fake-api-key", "fake-cookie", "fake-auth-code");
        // 로그 처리 이후에도 애플리케이션에서 사용하는 예외 연결 구조 유지 여부 검증
        assertThat(failure.getCause().getMessage()).contains("fake-api-key");
        assertThat(failure.getSuppressed()).hasSize(1);
    }

    @Test
    void businessFailureResponseAndLoggingBehaviorRemainUnchanged(CapturedOutput output) {
        var response = handler.handleGeneralException(new GeneralException(GeneralErrorCode.INVALID_REQUEST));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("COMMON4001");
        assertThat(response.getBody().message()).isEqualTo(GeneralErrorCode.INVALID_REQUEST.getMessage());
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(output.getAll()).isEmpty();
    }
}

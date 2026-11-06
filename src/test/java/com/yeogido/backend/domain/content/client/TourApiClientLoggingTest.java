package com.yeogido.backend.domain.content.client;

import com.yeogido.backend.domain.content.exception.ContentErrorCode;
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
class TourApiClientLoggingTest {

    private TourApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TourApiClient(builder);
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.com/KorService2");
        ReflectionTestUtils.setField(client, "serviceKey", "secret-service-key");
        ReflectionTestUtils.setField(client, "mobileOs", "ETC");
        ReflectionTestUtils.setField(client, "mobileApp", "Yeogido");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    void httpFailureKeepsStatusButNotResponseBodyOrRequestUrl(int status, CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("Authorization: Bearer secret-access-token; Cookie: secret-cookie; secret-provider-body"));

        assertApiError();

        assertThat(output.getAll()).contains("provider=TOUR_API", "path=/detailCommon2",
                "failureType=http_error", "httpStatus=" + status);
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

        assertThat(output.getAll()).contains("provider=TOUR_API", "path=/detailCommon2", "failureType=timeout");
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void connectionFailureKeepsClassificationWithoutUrl(CapturedOutput output) {
        server.expect(anything()).andRespond(withException(new IOException("secret-connection-message")));

        assertApiError();

        assertThat(output.getAll()).contains("failureType=connection_error");
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
        assertThat(output.getAll()).contains("provider=TOUR_API", "path=/detailCommon2",
                "failureType=invalid_response", "resultCode=" + expected);
        assertNoSecrets(output);
        server.verify();
    }

    @Test
    void malformedResponseDoesNotExposeParserInput(CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{secret-provider-body", MediaType.APPLICATION_JSON));

        assertApiError();

        assertThat(output.getAll()).contains("failureType=response_parse_error");
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

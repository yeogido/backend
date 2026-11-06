package com.yeogido.backend.domain.user.client;

import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class NtsBusinessVerificationClientLoggingTest {

    private static final String BUSINESS_NUMBER = "9876543210";
    private static final String SECRET = "private-nts-api-key";
    private static final String PRIVATE_RESPONSE = "private-provider-body";
    private NtsBusinessVerificationClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NtsBusinessVerificationClient(builder);
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.com");
        ReflectionTestUtils.setField(client, "serviceKey", SECRET);
    }

    @ParameterizedTest
    @CsvSource({"validate,401", "validate,429", "validate,503", "status,401", "status,429", "status,503"})
    void httpFailuresKeepStatusWithoutProviderBody(String operation, int status, CapturedOutput output) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body(SECRET + " " + BUSINESS_NUMBER + " " + PRIVATE_RESPONSE));

        assertApiError(operation);

        assertThat(output.getAll()).contains("provider=NTS", "operation=" + operation,
                        "failureType=http_error", "httpStatus=" + status);
        assertNoPrivateData(output);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"validate,true", "validate,false", "status,true", "status,false"})
    void transportFailuresDoNotLogUrlOrExceptionDetails(String operation, boolean timeout, CapturedOutput output) {
        String privateUrl = "https://example.com?serviceKey=" + SECRET;
        IOException failure = timeout ? new SocketTimeoutException(privateUrl) : new IOException(privateUrl);
        failure.initCause(new IllegalStateException("Authorization: Bearer private-token"));
        failure.addSuppressed(new IllegalArgumentException("Cookie: private-cookie"));
        server.expect(anything()).andRespond(withException(failure));

        assertApiError(operation);

        assertThat(output.getAll()).contains("provider=NTS", "operation=" + operation,
                "failureType=" + (timeout ? "timeout" : "connection_error"));
        assertNoPrivateData(output);
        assertThat(output.getAll()).doesNotContain("private-token", "private-cookie", "https://example.com");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"validate", "status"})
    void invalidResponseDoesNotLogDtoOrUntrustedStatus(String operation, CapturedOutput output) {
        String body = "{\"status_code\":\"" + PRIVATE_RESPONSE + "\",\"data\":[{\"b_no\":\""
                + BUSINESS_NUMBER + "\",\"valid_msg\":\"" + SECRET + "\"}]}";
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertApiError(operation);

        assertThat(output.getAll()).contains("provider=NTS", "operation=" + operation, "failureType=invalid_response");
        assertNoPrivateData(output);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"validate,''", "validate,null", "status,''", "status,null"})
    void emptyResponsePreservesApiError(String operation, String body, CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertApiError(operation);

        assertThat(output.getAll()).contains("provider=NTS", "operation=" + operation, "failureType=invalid_response");
        assertNoPrivateData(output);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"validate", "status"})
    void malformedResponseKeepsParseClassificationWithoutOriginalBody(String operation, CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"status_code\":\"" + SECRET + "\" broken "
                + PRIVATE_RESPONSE, MediaType.APPLICATION_JSON));

        assertApiError(operation);

        assertThat(output.getAll()).contains("provider=NTS", "operation=" + operation,
                "failureType=response_parse_error");
        assertNoPrivateData(output);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"validate", "status"})
    void successfulResponseRemainsUnchanged(String operation, CapturedOutput output) {
        String body = "{\"status_code\":\"OK\",\"data\":[{\"b_no\":\"" + BUSINESS_NUMBER
                + "\",\"valid\":\"01\",\"b_stt_cd\":\"01\"}]}";
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        if ("validate".equals(operation)) {
            var result = client.verify(BUSINESS_NUMBER, LocalDate.of(2020, 1, 1), "private-person");
            assertThat(result.businessNumber()).isEqualTo(BUSINESS_NUMBER);
            assertThat(result.valid()).isEqualTo("01");
        } else {
            var result = client.getBusinessStatus(BUSINESS_NUMBER);
            assertThat(result.businessNumber()).isEqualTo(BUSINESS_NUMBER);
            assertThat(result.businessStatusCode()).isEqualTo("01");
        }
        assertNoPrivateData(output);
        server.verify();
    }

    private void assertApiError(String operation) {
        assertThatThrownBy(() -> {
            if ("validate".equals(operation)) {
                client.verify(BUSINESS_NUMBER, LocalDate.of(2020, 1, 1), "private-person");
            } else {
                client.getBusinessStatus(BUSINESS_NUMBER);
            }
        }).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR))
                .hasNoCause();
    }

    private void assertNoPrivateData(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(SECRET, BUSINESS_NUMBER, PRIVATE_RESPONSE, "private-person");
    }
}

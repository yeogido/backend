package com.yeogido.backend.domain.user.client;

import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 Mock 모드({@code NTS_MOCK_ENABLED=true})의 끝자리 규칙.
 * 실제 HTTP 호출이 나가지 않아야 하므로 MockRestServiceServer에 기대 요청을 하나도 등록하지 않는다.
 */
class NtsBusinessVerificationClientMockTest {

    private NtsBusinessVerificationClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NtsBusinessVerificationClient(builder);
        ReflectionTestUtils.setField(client, "mockEnabled", true);
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.com");
        ReflectionTestUtils.setField(client, "serviceKey", "mock-key");
    }

    @ParameterizedTest
    @CsvSource({
            "1234567890, 01",
            "1234567891, 01",
            "1234567892, 02",
            "1234567893, 03",
            "1234567899, ''"
    })
    void getBusinessStatus_UsesLastDigitRule(String businessNumber, String expectedStatusCode) {
        NtsBusinessVerifyDTO.Status status = client.getBusinessStatus(businessNumber);

        assertThat(status.businessNumber()).isEqualTo(businessNumber);
        assertThat(status.businessStatusCode()).isEqualTo(expectedStatusCode);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "1234567890, 01, 01",
            "1234567892, 01, 02",
            "1234567893, 01, 03"
    })
    void verify_WhenLastDigitIsNot8Or9_ReturnsValidWithStatus(
            String businessNumber,
            String expectedValid,
            String expectedStatusCode
    ) {
        NtsBusinessVerifyDTO.Result result = client.verify(
                businessNumber, LocalDate.of(2024, 1, 15), "홍길동"
        );

        assertThat(result.valid()).isEqualTo(expectedValid);
        assertThat(result.status()).isNotNull();
        assertThat(result.status().businessStatusCode()).isEqualTo(expectedStatusCode);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"1234567898", "1234567899"})
    void verify_WhenLastDigitIs8Or9_ReturnsInvalidWithoutStatus(String businessNumber) {
        NtsBusinessVerifyDTO.Result result = client.verify(
                businessNumber, LocalDate.of(2024, 1, 15), "홍길동"
        );

        assertThat(result.valid()).isEqualTo("02");
        assertThat(result.status()).isNull();
        server.verify();
    }
}

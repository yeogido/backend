package com.yeogido.backend.domain.content.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import java.time.Duration;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class TourApiPlaceSearchTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"returnReasonCode\":\"22\",\"errMsg\":\"private-provider-body\"}",
            "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>private-provider-body</errMsg><returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"
    })
    void recognizesDailyQuotaOnHttp429WithoutExposingProviderBody(String body,
            org.springframework.boot.test.system.CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = configuredClient(builder);
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "120").body(body));
        assertThatThrownBy(() -> client.searchPlaces("private-query", "11", null, 1, 20))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(ExternalApiFailureKind.DAILY_QUOTA);
                    assertThat(failure.getUpstreamHttpStatus()).isEqualTo(429);
                    assertThat(failure.getProviderCode()).isEqualTo("22");
                    assertThat(failure.getRetryAfter()).isEqualTo(Duration.ofSeconds(120));
                    assertThat(failure.getOperation()).isEqualTo("/searchKeyword2");
                }).hasNoCause();
        assertThat(output.getOut()).doesNotContain("private-provider-body", "test-key", "private-query");
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>",
            "{\"response\":{\"header\":{\"resultCode\":\"22\"}}}"
    })
    void recognizesDailyQuotaInHttp200ErrorEnvelope(String body) {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = configuredClient(builder);
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_XML));
        assertThatThrownBy(() -> client.searchPlaces("query", null, null, 1, 20))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure ->
                        assertThat(failure.getKind()).isEqualTo(ExternalApiFailureKind.DAILY_QUOTA));
        server.verify();
    }

    @Test
    void unknown429RemainsRateLimitAndHonorsRetryAfter() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = configuredClient(builder);
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "45").body("unrecognized body"));
        assertThatThrownBy(() -> client.detailCommon("123"))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(ExternalApiFailureKind.RATE_LIMIT);
                    assertThat(failure.getRetryAfter()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(failure.getOperation()).isEqualTo("/detailCommon2");
                });
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "<!DOCTYPE OpenAPI_ServiceResponse [<!ENTITY data SYSTEM 'file:///nonexistent-secret'>]><OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>&data;</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>",
            "<!DOCTYPE OpenAPI_ServiceResponse [<!ENTITY data '22'>]><OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>&data;</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>",
            "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>22</returnReasonCode>"
    })
    void malformedXmlAndEntitiesDoNotProduceTrustedDailyQuota(String body) {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = configuredClient(builder);
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body(body));
        assertThatThrownBy(() -> client.searchPlaces("query", null, null, 1, 20))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(ExternalApiFailureKind.RATE_LIMIT);
                    assertThat(failure.getProviderCode()).isNull();
                });
        server.verify();
    }

    @Test
    void oversizedErrorBodyDoesNotOverrideStatusClassification() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = configuredClient(builder);
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("<returnReasonCode>22</returnReasonCode>" + "x".repeat(65536)));
        assertThatThrownBy(() -> client.searchPlaces("query", null, null, 1, 20))
                .isInstanceOfSatisfying(ExternalApiFailure.class, failure -> {
                    assertThat(failure.getKind()).isEqualTo(ExternalApiFailureKind.RATE_LIMIT);
                    assertThat(failure.getProviderCode()).isNull();
                });
        server.verify();
    }

    private TourApiClient configuredClient(RestClient.Builder builder) {
        var client = new TourApiClient(builder);
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.com/KorService2");
        ReflectionTestUtils.setField(client, "serviceKey", "test-key");
        ReflectionTestUtils.setField(client, "mobileOs", "ETC");
        ReflectionTestUtils.setField(client, "mobileApp", "Yeogido");
        return client;
    }

    @Test
    void encodesKeywordAndUsesLegalDistrictFilters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder);
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.com/KorService2");
        ReflectionTestUtils.setField(client, "serviceKey", "test-key");
        ReflectionTestUtils.setField(client, "mobileOs", "ETC");
        ReflectionTestUtils.setField(client, "mobileApp", "Yeogido");
        server.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/KorService2/searchKeyword2");
            assertThat(request.getURI().getRawQuery()).contains("keyword=%EC%84%9C%EC%9A%B8%20A%26B");
        }).andExpect(queryParam("lDongRegnCd", "11"))
                .andExpect(queryParam("lDongSignguCd", "440"))
                .andExpect(queryParam("pageNo", "2"))
                .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"0000\"}}}", MediaType.APPLICATION_JSON));
        client.searchPlaces("서울 A&B", "11", "440", 2, 20);
        server.verify();
    }
}

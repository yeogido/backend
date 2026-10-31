package com.yeogido.backend.domain.content.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TourApiPlaceSearchTest {
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

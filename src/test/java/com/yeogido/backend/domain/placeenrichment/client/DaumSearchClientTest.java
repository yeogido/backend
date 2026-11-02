package com.yeogido.backend.domain.placeenrichment.client;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DaumSearchClientTest {
    private final TourPlaceRequest.Input input = new TourPlaceRequest.Input(
            "1", "카페", null, null, "서울 테스트로 1", null, null, null);

    @Test
    void searchesWithNameAndAddressStripsMarkupAndDeduplicatesUrls() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DaumSearchClient client = new DaumSearchClient(builder.build(), "secret-key", 5);
        String body = """
                {"documents":[{"url":"https://blog.example/post","title":"<b>카페</b> &amp; 빵",
                "contents":"서울 테스트로 1"}]}
                """;
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/search/blog?query=")))
                .andExpect(request -> assertThat(java.net.URLDecoder.decode(
                        request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8))
                        .contains("query=카페 서울 테스트로 1"))
                .andExpect(header("Authorization", "KakaoAK secret-key"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/search/web?query=")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/search/blog?query=")))
                .andExpect(request -> assertThat(java.net.URLDecoder.decode(
                        request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8))
                        .contains("query=카페 서울 테스트로 1 주요 활동 체험"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/search/blog?query=")))
                .andExpect(request -> assertThat(java.net.URLDecoder.decode(
                        request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8))
                        .contains("query=카페 서울 테스트로 1 공간 좌석 실내 야외 분위기"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var sources = client.search(input);
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().title()).isEqualTo("카페 & 빵");
        assertThat(sources.getFirst().snippet()).isEqualTo("서울 테스트로 1");
        assertThat(sources.getFirst().sourceType().name()).isEqualTo("REVIEW");
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CE7,메뉴 음료 디저트", "FD6,메뉴 음식", "CT1,전시 관람 체험"})
    void searchesOfferingsForKnownCategoryWithoutIncreasingCallLimit(String category, String keywords) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int i = 0; i < 4; i++) {
            var expectation = server.expect(anything());
            if (i == 2) expectation.andExpect(request -> assertThat(java.net.URLDecoder.decode(
                    request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8)).contains(keywords));
            expectation.andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        }
        var place = new TourPlaceRequest.Input("1", "장소", null, category, "서울 테스트로 1", null, null, null);
        assertThat(new DaumSearchClient(builder.build(), "secret", 5).search(place)).isEmpty();
        server.verify();
    }

    @Test
    void preservesDifferentExcerptsForSameUrlAndStableSourceIds() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String[] snippets = {"서울 테스트로 1", "실내 창가 좌석", "테라스 공간", "가을 풍경"};
        for (int i = 0; i < snippets.length; i++) {
            String extraDocument = i == 3
                    ? ",{\"url\":\"https://blog.example/second\",\"title\":\"두 번째 출처\",\"contents\":\"전시 공간\"}" : "";
            server.expect(anything()).andRespond(withSuccess(
                    "{\"documents\":[{\"url\":\"https://blog.example/post\",\"title\":\"카페\",\"contents\":\""
                            + snippets[i] + "\"}" + extraDocument + "]}", MediaType.APPLICATION_JSON));
        }
        java.time.Instant first = java.time.Instant.parse("2026-11-02T01:00:00Z");
        java.time.Instant later = first.plusSeconds(60);
        java.util.List<com.yeogido.backend.domain.placeenrichment.dto.response.DaumSearchResponse.SearchSource> sources;
        try (var time = org.mockito.Mockito.mockStatic(java.time.Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            time.when(java.time.Instant::now).thenReturn(first, later);
            sources = new DaumSearchClient(builder.build(), "secret-key", 5).search(input);
        }
        assertThat(sources).hasSize(2);
        assertThat(sources.getFirst().sourceId()).isEqualTo("S1");
        assertThat(sources.getFirst().sourceType().name()).isEqualTo("REVIEW");
        assertThat(sources.getFirst().snippet()).isEqualTo(String.join("\n[별도 검색 발췌]\n", snippets));
        assertThat(sources.get(1).sourceId()).isEqualTo("S2");
        assertThat(sources.getFirst().collectedAt()).isEqualTo(first);
        assertThat(sources.get(1).collectedAt()).isEqualTo(later);
        server.verify();
    }

    @Test
    void quotaFailureStopsWithoutRetryOrLeakingCredentials() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("secret-key -10"));
        assertThatThrownBy(() -> new DaumSearchClient(builder.build(), "secret-key", 5).search(input))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR)).hasMessageNotContaining("secret-key").hasNoCause();
        server.verify();
    }

    @Test
    void missingKeyFailsBeforeRequest() {
        assertThatThrownBy(() -> new DaumSearchClient(RestClient.create(), "", 5).search(input))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG));
    }
}

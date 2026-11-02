package com.yeogido.backend.domain.placeenrichment.client;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiSearchClientTest {

    @Test
    void usesOrdinaryGenerationAndKeepsCredentialsOutOfUrl() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiSearchClient client = new GeminiSearchClient(builder.build(), "secret-key", "gemini-2.5-flash");
        server.expect(requestTo("https://example.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("instructions"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("input"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.search("instructions", "input")).isEqualTo(new ObjectMapper().createObjectNode());
        server.verify();
    }

    @Test
    void apiErrorsDoNotExposeProviderBodiesOrBecomeNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiSearchClient client = new GeminiSearchClient(builder.build(), "secret-key", "gemini-2.5-flash");
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret-key provider body"));

        assertThatThrownBy(() -> client.search("instructions", "input")).isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR)).hasMessageNotContaining("secret-key").hasNoCause();
        server.verify();
    }

    @Test
    void missingKeyAndInvalidModelFailBeforeRequest() {
        RestClient client = RestClient.create();
        assertThatThrownBy(() -> new GeminiSearchClient(client, "", "gemini-2.5-flash").search("a", "b"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG));
        assertThatThrownBy(() -> new GeminiSearchClient(client, "key", "../bad").search("a", "b"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG));
    }
}

package com.yeogido.backend.domain.placeenrichment.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiSearchClient {

    private final RestClient client;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiSearchClient(RestClient.Builder builder,
                              @Value("${app.gemini.api-key:}") String apiKey,
                              @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                              @Value("${app.gemini.model:gemini-3.5-flash-lite}") String model,
                              @Value("${app.gemini.timeout-seconds:60}") int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 600) {
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    GeminiSearchClient(RestClient client, String apiKey, String model) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
    }

    public JsonNode search(String instructions, String inputJson) {
        if (!StringUtils.hasText(apiKey)) {
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
        }
        if (model == null || !model.matches("[a-zA-Z0-9._-]+")) {
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
        }
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", inputJson)))),
                "generationConfig", Map.of("temperature", 0.0, "maxOutputTokens", 8192, "responseMimeType", "application/json"));
        try {
            return client.post().uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR);
        } catch (RestClientException exception) {
            // 인증 정보와 제공자 응답 노출 방지를 위한 예외 원인 제외
            throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR);
        }
    }
}

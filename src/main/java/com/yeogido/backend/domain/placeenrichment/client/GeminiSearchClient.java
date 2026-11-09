package com.yeogido.backend.domain.placeenrichment.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import com.yeogido.backend.global.logging.SafeLogDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
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
            throw configurationFailure();
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
        return PlaceOverviewApiScope.call("gemini", "generateContent", () -> searchContent(instructions, inputJson));
    }

    private JsonNode searchContent(String instructions, String inputJson) {
        if (!StringUtils.hasText(apiKey)) {
            throw configurationFailure();
        }
        if (model == null || !model.matches("[a-zA-Z0-9._-]+")) {
            throw configurationFailure();
        }
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", inputJson)))),
                "generationConfig", Map.of("temperature", 0.0, "maxOutputTokens", 8192, "responseMimeType", "application/json"));
        try {
            JsonNode response = client.post().uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
            if (response == null || response.isNull()) {
                logFailure("response_parse_error", null);
                throw new ExternalApiFailure(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR,
                        "gemini", "generateContent", ExternalApiFailureKind.INVALID_RESPONSE, null, null, null);
            }
            return response;
        } catch (RestClientResponseException exception) {
            logFailure(SafeLogDetails.httpFailureType(exception.getStatusCode().value()), exception.getStatusCode().value());
            throw ExternalApiFailure.http(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR,
                    "gemini", "generateContent", exception);
        } catch (RestClientException | HttpMessageConversionException exception) {
            logFailure(SafeLogDetails.failureType(exception), null);
            throw ExternalApiFailure.client(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR,
                    "gemini", "generateContent", exception);
        }
    }

    private static ExternalApiFailure configurationFailure() {
        return new ExternalApiFailure(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG,
                "gemini", "generateContent", ExternalApiFailureKind.CONFIGURATION, null, null, null);
    }

    private void logFailure(String failureType, Integer status) {
        var entry = log.atWarn()
                .addKeyValue("event", "external_api.call.failed")
                .addKeyValue("provider", "gemini")
                .addKeyValue("operation", "generateContent")
                .addKeyValue("failureType", failureType)
                .addKeyValue("errorCode", PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR.getCode())
                .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, status));
        if (status != null) {
            entry.addKeyValue("upstreamHttpStatus", status);
        }
        entry.log("External API call failed");
    }
}

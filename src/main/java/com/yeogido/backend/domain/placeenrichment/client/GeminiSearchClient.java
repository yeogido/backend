package com.yeogido.backend.domain.placeenrichment.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
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
import org.springframework.web.client.ResourceAccessException;
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
            JsonNode response = client.post().uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
            if (response == null || response.isNull()) {
                log.warn("Gemini generateContent failed. failureType=response_parse_error");
                throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR);
            }
            return response;
        } catch (RestClientResponseException exception) {
            log.warn("Gemini generateContent failed. failureType={}, httpStatus={}",
                    classifyHttpStatus(exception.getStatusCode().value()), exception.getStatusCode().value());
            throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR);
        } catch (RestClientException | HttpMessageConversionException exception) {
            log.warn("Gemini generateContent failed. failureType={}", classifyClientFailure(exception));
            throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR);
        }
    }

    private String classifyHttpStatus(int status) {
        if (status == 401) return "authentication_failure";
        if (status == 403) return "permission_or_access_failure";
        if (status == 429) return "quota_or_rate_limit";
        if (status >= 400 && status < 500) return "request_error";
        if (status >= 500) return "provider_server_error";
        return "unexpected_http_status";
    }

    private String classifyClientFailure(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpMessageConversionException) return "response_parse_error";
            if (cause instanceof SocketTimeoutException) return "timeout";
        }
        if (exception instanceof ResourceAccessException) return "connection_error";
        return "client_or_response_error";
    }
}

package com.yeogido.backend.domain.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CourseEmbeddingClient {

  private static final int BATCH_SIZE = 20;
  private final RestClient client;
  private final boolean enabled;
  private final String apiKey;
  private final String model;
  private final int dimensions;

  @Autowired
  public CourseEmbeddingClient(RestClient.Builder builder,
      @Value("${app.course-search.embedding.enabled:false}") boolean enabled,
      @Value("${app.gemini.api-key:}") String apiKey,
      @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
      @Value("${app.course-search.embedding.model:gemini-embedding-001}") String model,
      @Value("${app.course-search.embedding.dimensions:768}") int dimensions,
      @Value("${app.course-search.embedding.timeout-seconds:10}") int timeoutSeconds) {
    this.enabled = enabled && StringUtils.hasText(apiKey);
    this.apiKey = apiKey;
    this.model = model;
    this.dimensions = dimensions;
    if (!this.enabled) {
      this.client = null;
      return;
    }
    if (timeoutSeconds < 1 || timeoutSeconds > 600 || dimensions < 128 || dimensions > 3072
        || model == null || !model.matches("[a-zA-Z0-9._-]+")) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    try {
      this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    } catch (IllegalArgumentException exception) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
  }

  CourseEmbeddingClient(RestClient client, String apiKey, String model, int dimensions) {
    this.client = client;
    this.enabled = StringUtils.hasText(apiKey);
    this.apiKey = apiKey;
    this.model = model;
    this.dimensions = dimensions;
  }

  public boolean isEnabled() { return enabled; }

  public String getModel() { return model; }

  public int getDimensions() { return dimensions; }

  public List<List<Double>> embedDocuments(List<String> documents) {
    requireEnabled();
    if (documents == null || documents.stream().anyMatch(text -> !StringUtils.hasText(text))) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
    List<List<Double>> result = new ArrayList<>(documents.size());
    for (int offset = 0; offset < documents.size(); offset += BATCH_SIZE) {
      List<String> batch = documents.subList(offset, Math.min(offset + BATCH_SIZE, documents.size()));
      var requests = batch.stream().map(text -> request(text, "RETRIEVAL_DOCUMENT")).toList();
      JsonNode response = post("batchEmbedContents", Map.of("requests", requests));
      JsonNode embeddings = response == null ? null : response.get("embeddings");
      if (embeddings == null || !embeddings.isArray() || embeddings.size() != batch.size()) {
        throw invalidResponse();
      }
      for (JsonNode embedding : embeddings) {
        result.add(normalize(embedding));
      }
    }
    return List.copyOf(result);
  }

  public List<Double> embedQuery(String query) {
    requireEnabled();
    if (!StringUtils.hasText(query)) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
    JsonNode response = post("embedContent", request(query, "RETRIEVAL_QUERY"));
    return normalize(response == null ? null : response.get("embedding"));
  }

  private Map<String, Object> request(String text, String taskType) {
    return Map.of("model", "models/" + model,
        "content", Map.of("parts", List.of(Map.of("text", text))),
        "taskType", taskType, "outputDimensionality", dimensions);
  }

  private JsonNode post(String operation, Map<String, Object> request) {
    try {
      return client.post().uri("/models/{model}:{operation}", model, operation)
          .header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
          .body(request).retrieve().body(JsonNode.class);
    } catch (RestClientException exception) {
      // Provider exceptions may contain credentials or response bodies; do not retain causes.
      throw new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR);
    }
  }

  private List<Double> normalize(JsonNode embedding) {
    JsonNode values = embedding == null ? null : embedding.get("values");
    if (values == null || !values.isArray() || values.size() != dimensions) {
      throw invalidResponse();
    }
    double norm = 0;
    List<Double> vector = new ArrayList<>(dimensions);
    for (JsonNode value : values) {
      if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
        throw invalidResponse();
      }
      double number = value.doubleValue();
      vector.add(number);
      norm = Math.hypot(norm, number);
    }
    if (!Double.isFinite(norm) || norm == 0) {
      throw invalidResponse();
    }
    double length = norm;
    return vector.stream().map(value -> value / length).toList();
  }

  private void requireEnabled() {
    if (!enabled) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
  }

  private GeneralException invalidResponse() {
    return new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
  }
}

package com.yeogido.backend.domain.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.placeenrichment.client.GeminiSearchClient;
import com.yeogido.backend.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class CourseSearchClient {

  private final GeminiSearchClient delegate;
  private final boolean enabled;

  @Autowired
  public CourseSearchClient(RestClient.Builder builder,
      @Value("${app.course-search.enabled:false}") boolean enabled,
      @Value("${app.gemini.api-key:}") String apiKey,
      @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
      @Value("${app.gemini.model:gemini-3.5-flash-lite}") String model,
      @Value("${app.course-search.timeout-seconds:3}") int timeoutSeconds) {
    this.enabled = enabled && StringUtils.hasText(apiKey);
    if (!this.enabled) {
      this.delegate = null;
      return;
    }
    if (timeoutSeconds < 1 || timeoutSeconds > 600
        || model == null || !model.matches("[a-zA-Z0-9._-]+")) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
    try {
      this.delegate = new GeminiSearchClient(builder, apiKey, baseUrl, model, timeoutSeconds);
    } catch (GeneralException | IllegalArgumentException exception) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
  }

  CourseSearchClient(GeminiSearchClient delegate, boolean enabled) {
    this.delegate = delegate;
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public JsonNode search(String instructions, String inputJson) {
    if (!enabled) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
    try {
      return delegate.search(instructions, inputJson);
    } catch (GeneralException exception) {
      // 장소 보완 도메인 오류의 코스 검색 경계 외부 노출 방지
      throw new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR);
    }
  }
}

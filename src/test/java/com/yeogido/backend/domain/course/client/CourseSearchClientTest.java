package com.yeogido.backend.domain.course.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.placeenrichment.client.GeminiSearchClient;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CourseSearchClientTest {

  @Test
  void disabledFeatureDoesNotValidateUnusedProviderConfiguration() {
    RestClient.Builder builder = mock(RestClient.Builder.class);

    var client = new CourseSearchClient(builder, false, "key", "invalid", null, 0);

    assertThat(client.isEnabled()).isFalse();
    verifyNoInteractions(builder);
  }

  @Test
  void missingApiKeyDisablesSearchWithoutConstructingProvider() {
    RestClient.Builder builder = mock(RestClient.Builder.class);

    var client = new CourseSearchClient(builder, true, " ", "invalid", null, 0);

    assertThat(client.isEnabled()).isFalse();
    verifyNoInteractions(builder);
  }

  @Test
  void activeInvalidTimeoutOrModelUsesCourseConfigurationError() {
    RestClient.Builder builder = mock(RestClient.Builder.class);

    assertConfigError(() -> new CourseSearchClient(builder, true, "key", "https://example.com", "model", 0));
    assertConfigError(() -> new CourseSearchClient(builder, true, "key", "https://example.com", "model", 601));
    assertConfigError(() -> new CourseSearchClient(builder, true, "key", "https://example.com", "bad/model", 3));
    assertConfigError(() -> new CourseSearchClient(builder, true, "key", "https://example.com", null, 3));
    verifyNoInteractions(builder);
  }

  @Test
  void providerConstructionFailureUsesCourseConfigurationError() {
    RestClient.Builder builder = mock(RestClient.Builder.class);
    when(builder.clone()).thenThrow(new IllegalArgumentException("invalid configuration"));

    assertConfigError(() -> new CourseSearchClient(builder, true, "key", "https://example.com", "model", 3));
  }

  @Test
  void mapsPlaceProviderErrorToCourseSearchError() {
    GeminiSearchClient delegate = mock(GeminiSearchClient.class);
    when(delegate.search("instructions", "input"))
        .thenThrow(new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR));
    var client = new CourseSearchClient(delegate, true);

    assertThatThrownBy(() -> client.search("instructions", "input"))
        .isInstanceOfSatisfying(GeneralException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.COURSE_SEARCH_API_ERROR));
  }

  @Test
  void forwardsSuccessfulProviderResponse() {
    GeminiSearchClient delegate = mock(GeminiSearchClient.class);
    var response = new ObjectMapper().createObjectNode().put("result", "ok");
    when(delegate.search("instructions", "input")).thenReturn(response);
    var client = new CourseSearchClient(delegate, true);

    assertThat(client.isEnabled()).isTrue();
    assertThat(client.search("instructions", "input")).isSameAs(response);
    verify(delegate).search("instructions", "input");
  }

  @Test
  void disabledSearchFailsWithoutCallingProvider() {
    GeminiSearchClient delegate = mock(GeminiSearchClient.class);
    var client = new CourseSearchClient(delegate, false);

    assertConfigError(() -> client.search("instructions", "input"));
    verifyNoInteractions(delegate);
  }

  private void assertConfigError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOfSatisfying(GeneralException.class,
        exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG));
  }
}

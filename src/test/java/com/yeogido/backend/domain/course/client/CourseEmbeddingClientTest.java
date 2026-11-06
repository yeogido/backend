package com.yeogido.backend.domain.course.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CourseEmbeddingClientTest {
  private MockRestServiceServer server;
  private CourseEmbeddingClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com/v1beta");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new CourseEmbeddingClient(builder.build(), "secret", "gemini-embedding-001", 2);
  }

  @Test
  void queryUsesRetrievalTaskAndNormalizesVector() {
    server.expect(requestTo("https://example.com/v1beta/models/gemini-embedding-001:embedContent"))
        .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "secret"))
        .andExpect(jsonPath("$.taskType").value("RETRIEVAL_QUERY"))
        .andExpect(jsonPath("$.content.parts[0].text").value("비오는 날 데이트"))
        .andExpect(jsonPath("$.outputDimensionality").value(2))
        .andRespond(withSuccess("{\"embedding\":{\"values\":[3,4]}}", MediaType.APPLICATION_JSON));

    assertThat(client.embedQuery("비오는 날 데이트")).containsExactly(0.6, 0.8);
    server.verify();
  }

  @Test
  void batchesDocumentsInOrderAndUsesDocumentTask() {
    server.expect(requestTo("https://example.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
        .andExpect(jsonPath("$.requests.length()").value(20))
        .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_DOCUMENT"))
        .andExpect(jsonPath("$.requests[0].model").value("models/gemini-embedding-001"))
        .andRespond(withSuccess("{\"embeddings\":[" + String.join(",", Collections.nCopies(20,
            "{\"values\":[3,4]}")) + "]}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://example.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andRespond(withSuccess("{\"embeddings\":[{\"values\":[0,2]}]}", MediaType.APPLICATION_JSON));

    var vectors = client.embedDocuments(Collections.nCopies(21, "실내 미술관"));
    assertThat(vectors).hasSize(21);
    assertThat(vectors.getFirst()).containsExactly(0.6, 0.8);
    assertThat(vectors.getLast()).containsExactly(0.0, 1.0);
    server.verify();
  }

  @Test
  void rejectsMalformedDimensionsZeroAndNonnumericResponses() {
    for (String response : List.of("{}", "{\"embedding\":{\"values\":[1]}}",
        "{\"embedding\":{\"values\":[0,0]}}", "{\"embedding\":{\"values\":[\"1\",2]}}")) {
      server.reset();
      server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
      assertError(() -> client.embedQuery("조용한"), CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
    }
  }

  @Test
  void rejectsMismatchingDocumentCount() {
    server.expect(anything()).andRespond(withSuccess("{\"embeddings\":[]}", MediaType.APPLICATION_JSON));
    assertError(() -> client.embedDocuments(List.of("코스")), CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
  }

  @Test
  void mapsHttpAndTransportErrorsWithoutProviderCause() {
    server.expect(anything()).andRespond(withServerError());
    assertError(() -> client.embedQuery("데이트"), CourseErrorCode.COURSE_SEARCH_API_ERROR);
    server.reset();
    server.expect(anything()).andRespond(withException(new IOException("secret")));
    assertError(() -> client.embedQuery("데이트"), CourseErrorCode.COURSE_SEARCH_API_ERROR);
  }

  @Test
  void disabledOrMissingKeyDoesNotConstructClient() {
    var disabled = new CourseEmbeddingClient(null, false, "key", "invalid", null, 0, 0);
    var missingKey = new CourseEmbeddingClient(null, true, " ", "invalid", null, 0, 0);
    assertThat(disabled.isEnabled()).isFalse();
    assertThat(missingKey.isEnabled()).isFalse();
    assertError(() -> disabled.embedQuery("데이트"), CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    assertError(() -> missingKey.embedDocuments(List.of("코스")), CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
  }

  @Test
  void validatesActiveConfigurationAndRejectsBlankInput() {
    assertError(() -> new CourseEmbeddingClient(null, true, "key", "https://example.com", "bad/model", 768, 10),
        CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    assertError(() -> new CourseEmbeddingClient(null, true, "key", "https://example.com", "model", 10, 10),
        CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    assertError(() -> new CourseEmbeddingClient(null, true, "key", "https://example.com", "model", 768, 0),
        CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    assertError(() -> client.embedQuery(" "), CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    assertError(() -> client.embedDocuments(List.of("")), CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    assertThat(client.embedDocuments(List.of())).isEmpty();
  }

  private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, CourseErrorCode code) {
    assertThatThrownBy(action).isInstanceOfSatisfying(GeneralException.class, exception -> {
      assertThat(exception.getErrorCode()).isEqualTo(code);
      assertThat(exception.getCause()).isNull();
    });
  }
}

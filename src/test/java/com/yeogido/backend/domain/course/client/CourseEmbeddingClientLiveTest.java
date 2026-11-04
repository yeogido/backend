package com.yeogido.backend.domain.course.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

@EnabledIfEnvironmentVariable(named = "RUN_COURSE_EMBEDDING_LIVE_TEST", matches = "true")
class CourseEmbeddingClientLiveTest {

  @Test
  void koreanSynonymsRankMatchingCoursesAboveUnrelatedOutdoorCourse() throws Exception {
    String apiKey = apiKey();
    assertThat(apiKey).as("GEMINI_API_KEY must be configured for the live test").isNotBlank();
    var client = new CourseEmbeddingClient(RestClient.builder(), true, apiKey,
        "https://generativelanguage.googleapis.com/v1beta", "gemini-embedding-001", 768, 10);
    var documents = client.embedDocuments(List.of(
        "한적한 숲속 북카페에서 독서하고 사람이 적은 정원을 느긋하게 산책하는 코스",
        "실내 미술관과 지붕 있는 전시관, 아늑한 카페를 방문해 우산 없이 즐기는 코스",
        "연인과 분위기 좋은 레스토랑에서 식사하고 야경을 함께 바라보는 로맨틱한 코스",
        "야외 놀이공원에서 신나는 놀이기구를 타고 시끄러운 거리 축제에서 춤추는 단체 코스"));
    List<String> queries = List.of("조용하게 쉬고 싶어", "비오는 날 갈만한 곳", "데이트하기 좋은 코스");
    for (int index = 0; index < queries.size(); index++) {
      var query = client.embedQuery(queries.get(index));
      double positive = dot(query, documents.get(index));
      double negative = dot(query, documents.getLast());
      System.out.printf("Embedding fixture '%s': positive=%.4f unrelated=%.4f%n",
          queries.get(index), positive, negative);
      assertThat(positive).as(queries.get(index)).isGreaterThan(negative);
    }
  }

  private double dot(List<Double> first, List<Double> second) {
    double sum = 0;
    for (int index = 0; index < first.size(); index++) {
      sum += first.get(index) * second.get(index);
    }
    return sum;
  }

  private String apiKey() throws Exception {
    String value = System.getenv("GEMINI_API_KEY");
    if (value != null && !value.isBlank()) return value;
    if (Files.exists(Path.of(".env"))) {
      for (String line : Files.readAllLines(Path.of(".env"))) {
        String trimmed = line.trim();
        if (trimmed.startsWith("GEMINI_API_KEY=")) {
          value = trimmed.substring("GEMINI_API_KEY=".length()).trim();
          if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
              || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
          }
          if (!value.isBlank()) return value;
        }
      }
    }
    throw new IllegalStateException("GEMINI_API_KEY is required for opt-in live test");
  }
}

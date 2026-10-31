package com.yeogido.backend.domain.placeenrichment.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.client.DaumSearchClient;
import com.yeogido.backend.domain.placeenrichment.client.GeminiSearchClient;
import com.yeogido.backend.domain.placeenrichment.service.GeminiPlaceEnrichmentService;
import com.yeogido.backend.domain.placeenrichment.service.GeminiPlaceEnrichmentServiceImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_FREE_PLACE_LIVE_TEST", matches = "true")
class FreePlaceEnrichmentLiveTest {
    @TempDir Path directory;

    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "2073184062,아일베이커리,CE7,서울 성북구 삼선교로11길 34,서울 성북구 삼선동4가 9-5",
            "1265675335,우아하게 연남,FD6,서울 마포구 연희로1길 45,서울 마포구 연남동 383"
    })
    void enrichesMissingOverviewThroughRealJavaClientsAndRunner(String id, String name, String category,
                                                               String roadAddress, String lotAddress) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var gemini = new GeminiSearchClient(RestClient.builder(), key("GEMINI_API_KEY"),
                "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash-lite", 60) {
            @Override
            public com.fasterxml.jackson.databind.JsonNode search(String instructions, String inputJson) {
                var response = super.search(instructions, inputJson);
                try {
                    Path artifacts = Path.of("build/place-enrichment");
                    Files.createDirectories(artifacts);
                    Files.writeString(artifacts.resolve("java-live-search-sources-" + id + ".json"), inputJson);
                    mapper.writerWithDefaultPrettyPrinter().writeValue(
                            artifacts.resolve("java-live-provider-response-" + id + ".json").toFile(), response);
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
                return response;
            }
        };
        var daum = new DaumSearchClient(RestClient.builder(), key("KAKAO_REST_API_KEY"),
                "https://dapi.kakao.com", 15, 5);
        var service = new GeminiPlaceEnrichmentServiceImpl(gemini, daum, mapper);
        Path input = directory.resolve("input.json");
        Path tour = directory.resolve("tour.json");
        Path output = Path.of("build/place-enrichment/" + ("2073184062".equals(id)
                ? "java-free-overview-result.json" : "java-free-overview-" + id + ".json")).toAbsolutePath();
        var place = mapper.createObjectNode().put("externalPlaceId", id).put("name", name)
                .put("categoryGroupCode", category).put("roadAddress", roadAddress).put("lotAddress", lotAddress);
        var inputs = mapper.createArrayNode();
        inputs.addObject().put("externalPlaceId", "existing").put("name", "기존 설명 장소");
        inputs.add(place);
        mapper.writeValue(input.toFile(), inputs);
        var tours = mapper.createArrayNode();
        tours.addObject().put("externalPlaceId", "existing").put("overview", "관광공사 기존 설명");
        tours.addObject().put("externalPlaceId", id).putNull("overview");
        mapper.writeValue(tour.toFile(), tours);
        var runner = new GeminiPlaceEnrichmentRunner(service, mapper);
        ReflectionTestUtils.setField(runner, "input", input.toString());
        ReflectionTestUtils.setField(runner, "tourResults", tour.toString());
        ReflectionTestUtils.setField(runner, "output", output.toString());
        ReflectionTestUtils.setField(runner, "maxRequests", 1);
        runner.run();
        var results = mapper.readTree(output.toFile());
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).path("overview").asText()).isEqualTo("관광공사 기존 설명");
        assertThat(results.get(0).path("overviewSource").asText()).isEqualTo("TOUR_API");
        assertThat(results.get(1).path("overviewSource").asText()).isEqualTo("DAUM_GEMINI");
        assertThat(results.get(1).path("overview").asText()).isNotBlank();
        assertThat(results.get(1).path("geminiResult").path("facts").size()).isPositive();
        assertThat(results.get(1).path("geminiResult").path("facts").valueStream()
                .anyMatch(fact -> "ACTIVITIES".equals(fact.path("category").asText()))).isTrue();
        if ("2073184062".equals(id)) {
            assertThat(results.get(1).path("geminiResult").path("facts").valueStream()
                    .anyMatch(fact -> "ATMOSPHERE".equals(fact.path("category").asText()))).isTrue();
        }
        assertThat(results.get(1).path("geminiResult").path("matchStatus").asText()).isEqualTo("VERIFIED");
    }

    private String key(String name) throws Exception {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) return value;
        if (Files.exists(Path.of(".env"))) {
            for (String line : Files.readAllLines(Path.of(".env"))) {
                String trimmed = line.trim();
                if (trimmed.startsWith(name + "=")) {
                    value = trimmed.substring(name.length() + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!value.isBlank()) return value;
                }
            }
        }
        throw new IllegalStateException(name + " is required for opt-in live test");
    }
}

package com.yeogido.backend.domain.placeenrichment.runner;

import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.service.GeminiPlaceEnrichmentService;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiPlaceEnrichmentRunnerTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiPlaceEnrichmentService service = mock(GeminiPlaceEnrichmentService.class);

    private GeminiPlaceEnrichmentRunner runner(String places, String tour, int limit) throws Exception {
        Files.writeString(directory.resolve("input.json"), places);
        Files.writeString(directory.resolve("tour.json"), tour);
        GeminiPlaceEnrichmentRunner runner = new GeminiPlaceEnrichmentRunner(service, mapper);
        ReflectionTestUtils.setField(runner, "input", directory.resolve("input.json").toString());
        ReflectionTestUtils.setField(runner, "tourResults", directory.resolve("tour.json").toString());
        ReflectionTestUtils.setField(runner, "output", directory.resolve("output.json").toString());
        ReflectionTestUtils.setField(runner, "maxRequests", limit);
        return runner;
    }

    private static final String PLACES = """
            [{"externalPlaceId":"1","name":"첫 장소","roadAddress":"서울 첫길 1"},
             {"externalPlaceId":"2","name":"둘째 장소","roadAddress":"서울 둘길 2"},
             {"externalPlaceId":"3","name":"셋째 장소","roadAddress":"서울 셋길 3"}]
            """;

    @Test
    void preservesTourOverviewAndCallsGeminiOnlyWithinLimit() throws Exception {
        GeminiPlaceResponse.Result generated = mapper.readValue("""
                {"id":"2","name":"둘째 장소","matchStatus":"VERIFIED","matchReason":"주소 일치",
                 "overview":"확인한 설명.","sources":[],"sentenceSources":[],"conflicts":[]}
                """, GeminiPlaceResponse.Result.class);
        when(service.enrich(any())).thenReturn(generated);
        runner(PLACES, """
                [{"externalPlaceId":"1","overview":"관광공사 설명"},
                 {"externalPlaceId":"2","overview":null},{"externalPlaceId":"3","overview":null}]
                """, 1).run();

        var results = mapper.readTree(directory.resolve("output.json").toFile());
        assertThat(results.get(0).path("overviewSource").asText()).isEqualTo("TOUR_API");
        assertThat(results.get(0).path("overview").asText()).isEqualTo("관광공사 설명");
        assertThat(results.get(1).path("overviewSource").asText()).isEqualTo("DAUM_GEMINI");
        assertThat(results.get(1).path("geminiResult").path("matchReason").asText()).isEqualTo("주소 일치");
        assertThat(results.get(2).path("status").asText()).isEqualTo("SKIPPED_LIMIT");
        verify(service).enrich(argThat(place -> place.externalPlaceId().equals("2")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void missingTourIdRejectsWholeBatchBeforeAnyCall() throws Exception {
        var runner = runner(PLACES, "[{\"externalPlaceId\":\"1\"}]", 1);
        assertThatThrownBy(runner::run).isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_RESPONSE));
        verifyNoInteractions(service);
        assertThat(directory.resolve("output.json")).doesNotExist();
    }

    @Test
    void duplicateIdsAreRejectedBeforeAnyCall() throws Exception {
        var runner = runner("""
                [{"externalPlaceId":"1","name":"장소","roadAddress":"서울 1"},
                 {"externalPlaceId":"1","name":"장소","roadAddress":"서울 1"}]
                """, "[{\"externalPlaceId\":\"1\"}]", 1);
        assertThatThrownBy(runner::run).isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_RESPONSE));
        verifyNoInteractions(service);
    }

    @Test
    void cannotOverwriteTourInputIncludingSymlink() throws Exception {
        var runner = runner("[]", "[]", 1);
        Files.createSymbolicLink(directory.resolve("output.json"), directory.resolve("tour.json"));
        assertThatThrownBy(runner::run).isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceEnrichmentErrorCode.ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT));
        assertThat(Files.readString(directory.resolve("tour.json"))).isEqualTo("[]");
        verifyNoInteractions(service);
    }

    @Test
    void providerFailurePreservesPreviouslyCompletedPlaces() throws Exception {
        var runner = runner(PLACES, """
                [{"externalPlaceId":"1","overview":"기존 설명"},
                 {"externalPlaceId":"2"},{"externalPlaceId":"3"}]
                """, 1);
        GeneralException failure = new GeneralException(PlaceEnrichmentErrorCode.GEMINI_GENERATION_API_ERROR);
        when(service.enrich(any())).thenThrow(failure);
        assertThatThrownBy(runner::run).isSameAs(failure);
        var results = mapper.readTree(directory.resolve("output.json").toFile());
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).path("overview").asText()).isEqualTo("기존 설명");
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("input.json", "tour.json", "output.json");
        }
    }

    @Test
    void emptyBatchWritesEmptyArrayWithoutSearch() throws Exception {
        runner("[]", "[]", 1).run();
        assertThat(mapper.readTree(directory.resolve("output.json").toFile()).isEmpty()).isTrue();
        verifyNoInteractions(service);
    }
}

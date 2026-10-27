package com.yeogido.backend.domain.placeenrichment.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.service.TourPlaceEnrichmentService;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TourPlaceEnrichmentRunnerTest {

    @TempDir
    Path directory;

    @Test
    void sameInputAndOutputPathUsesPlaceErrorCode() throws Exception {
        TourPlaceEnrichmentService service = mock(TourPlaceEnrichmentService.class);
        ObjectMapper mapper = new ObjectMapper();
        Path inputPath = directory.resolve("input.json");
        Files.writeString(inputPath, "[]");
        TourPlaceEnrichmentRunner runner = new TourPlaceEnrichmentRunner(service, mapper);
        ReflectionTestUtils.setField(runner, "input", inputPath.toString());
        ReflectionTestUtils.setField(runner, "output", directory.resolve("./input.json").toString());

        assertThatThrownBy(runner::run)
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlaceEnrichmentErrorCode.ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT));

        verifyNoInteractions(service);
        assertThat(Files.readString(inputPath)).isEqualTo("[]");
    }
}

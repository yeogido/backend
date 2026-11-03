package com.yeogido.backend.domain.placeenrichment.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.service.TourPlaceEnrichmentService;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TourPlaceEnrichmentRunnerTest {

    @TempDir
    Path directory;

    private final TourPlaceEnrichmentService service = mock(TourPlaceEnrichmentService.class);

    private TourPlaceEnrichmentRunner runner(ObjectMapper mapper, Path input, Path output) {
        TourPlaceEnrichmentRunner runner = new TourPlaceEnrichmentRunner(service, mapper);
        ReflectionTestUtils.setField(runner, "input", input.toString());
        ReflectionTestUtils.setField(runner, "output", output.toString());
        return runner;
    }

    @Test
    void emptyInputReplacesPreviousResultsWithEmptyArray() throws Exception {
        Path input = directory.resolve("input.json");
        Path output = directory.resolve("results.json");
        Files.writeString(input, "[]");
        Files.writeString(output, "[\"previous\"]");

        runner(new ObjectMapper(), input, output).run();

        assertThat(new ObjectMapper().readTree(output.toFile()))
                .isEqualTo(new ObjectMapper().createArrayNode());
        verifyNoInteractions(service);
    }

    @Test
    void emptyInputCreatesOutputFile() throws Exception {
        Path input = directory.resolve("input.json");
        Path output = directory.resolve("nested/results.json");
        Files.writeString(input, "[]");

        runner(new ObjectMapper(), input, output).run();

        assertThat(new ObjectMapper().readTree(output.toFile()))
                .isEqualTo(new ObjectMapper().createArrayNode());
    }

    @Test
    void symbolicLinkToInputIsRejected() throws Exception {
        Path input = directory.resolve("input.json");
        Files.writeString(input, "[]");
        Path output = Files.createSymbolicLink(directory.resolve("output.json"), input);

        assertPathConflict(input, output);
    }

    @Test
    void hardLinkToInputIsRejected() throws Exception {
        Path input = directory.resolve("input.json");
        Files.writeString(input, "[]");
        Path output = Files.createLink(directory.resolve("output.json"), input);

        assertPathConflict(input, output);
    }

    private void assertPathConflict(Path input, Path output) throws Exception {
        assertThatThrownBy(() -> runner(new ObjectMapper(), input, output).run())
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlaceEnrichmentErrorCode.ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT));
        assertThat(Files.readString(input)).isEqualTo("[]");
        verifyNoInteractions(service);
    }

    @Test
    void failedWritePreservesLastCompletedResultAndRemovesTemporaryFile() throws Exception {
        Path input = directory.resolve("input.json");
        Path output = directory.resolve("results.json");
        Files.writeString(input, "[{\"name\":\"첫 장소\"},{\"name\":\"둘째 장소\"}]");
        ObjectMapper mapper = spy(new ObjectMapper());
        ObjectWriter writer = spy(mapper.writerWithDefaultPrettyPrinter());
        doReturn(writer).when(mapper).writerWithDefaultPrettyPrinter();
        doCallRealMethod().doAnswer(invocation -> {
            Files.writeString(((File) invocation.getArgument(0)).toPath(), "[");
            throw new IOException("write failed");
        }).when(writer).writeValue(any(File.class), any());

        assertThatThrownBy(() -> runner(mapper, input, output).run())
                .isInstanceOf(IOException.class).hasMessage("write failed");

        assertThat(new ObjectMapper().readTree(output.toFile()).size()).isEqualTo(1);
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("input.json", "results.json");
        }
        verify(service, times(2)).enrich(any());
    }

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

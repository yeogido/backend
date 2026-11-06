package com.yeogido.backend.domain.placeenrichment.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.converter.TourPlaceConverter;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.service.TourPlaceEnrichmentService;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.tour-api.place-enrichment", name = "input")
@RequiredArgsConstructor
@Slf4j
public class TourPlaceEnrichmentRunner implements CommandLineRunner {

    private final TourPlaceEnrichmentService enrichmentService;
    private final ObjectMapper objectMapper;

    @Value("${app.tour-api.place-enrichment.input}")
    private String input;

    @Value("${app.tour-api.place-enrichment.output:build/place-enrichment/results.json}")
    private String output;

    @Override
    public void run(String... args) throws Exception {
        Path inputPath = Path.of(input).toAbsolutePath().normalize();
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        if (outputPath.equals(inputPath)
                || Files.exists(outputPath) && Files.isSameFile(inputPath, outputPath)) {
            throw new GeneralException(PlaceEnrichmentErrorCode.ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT);
        }
        List<TourPlaceRequest.Input> places = objectMapper.readValue(inputPath.toFile(), new TypeReference<>() { });
        List<TourPlaceResponse.MatchResult> results = new ArrayList<>();
        Files.createDirectories(outputPath.getParent());
        if (places.isEmpty()) {
            saveResults(outputPath, results);
        }
        for (TourPlaceRequest.Input place : places) {
            results.add(enrichmentService.enrich(TourPlaceConverter.toPlace(place)));
            // 장소별 처리 완료 결과 저장
            saveResults(outputPath, results);
        }
        log.info("Tour place enrichment completed. count={}, output={}", results.size(), outputPath);
    }

    private void saveResults(Path outputPath, List<TourPlaceResponse.MatchResult> results) throws IOException {
        // 저장 실패 시 이전 완료 결과를 보존하도록 같은 디렉터리에서 원자적으로 교체
        Path temporary = Files.createTempFile(outputPath.getParent(), ".place-enrichment-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), results);
            Files.move(temporary, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

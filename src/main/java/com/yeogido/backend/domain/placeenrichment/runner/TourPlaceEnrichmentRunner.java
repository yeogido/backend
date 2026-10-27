package com.yeogido.backend.domain.placeenrichment.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.converter.TourPlaceConverter;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceReqDTO;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResDTO;
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
        List<TourPlaceReqDTO.Input> places = objectMapper.readValue(Path.of(input).toFile(), new TypeReference<>() { });
        List<TourPlaceResDTO.MatchResult> results = new ArrayList<>();
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        if (outputPath.equals(Path.of(input).toAbsolutePath().normalize())) {
            throw new GeneralException(PlaceEnrichmentErrorCode.ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT);
        }
        Files.createDirectories(outputPath.getParent());
        for (TourPlaceReqDTO.Input place : places) {
            results.add(enrichmentService.enrich(TourPlaceConverter.toPlace(place)));
            // 장소별 처리 완료 결과 저장
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), results);
        }
        log.info("Tour place enrichment completed. count={}, output={}", results.size(), outputPath);
    }
}

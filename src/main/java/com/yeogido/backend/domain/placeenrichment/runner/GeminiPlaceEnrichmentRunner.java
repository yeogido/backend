package com.yeogido.backend.domain.placeenrichment.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.dto.response.PlaceEnrichmentResponse.Result;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.service.GeminiPlaceEnrichmentService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.yeogido.backend.domain.placeenrichment.enums.GeminiPlaceMatchStatus.VERIFIED;
import static com.yeogido.backend.domain.placeenrichment.enums.PlaceEnrichmentStatus.*;
import static com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource.*;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.gemini.place-enrichment", name = "input")
@RequiredArgsConstructor
@Slf4j
public class GeminiPlaceEnrichmentRunner implements CommandLineRunner {
    private final GeminiPlaceEnrichmentService enrichmentService;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.place-enrichment.input}")
    private String input;
    @Value("${app.gemini.place-enrichment.tour-results}")
    private String tourResults;
    @Value("${app.gemini.place-enrichment.output:build/place-enrichment/gemini-results.json}")
    private String output;
    @Value("${app.gemini.place-enrichment.max-requests:1}")
    private int maxRequests;

    @Override
    public void run(String... args) throws Exception {
        if (maxRequests < 1) {
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
        }
        Path inputPath = Path.of(input).toAbsolutePath().normalize();
        Path tourPath = Path.of(tourResults).toAbsolutePath().normalize();
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        for (Path source : List.of(inputPath, tourPath)) {
            if (source.equals(outputPath) || Files.exists(outputPath) && Files.isSameFile(source, outputPath)) {
                throw new GeneralException(PlaceEnrichmentErrorCode.ENRICHMENT_INPUT_OUTPUT_PATH_CONFLICT);
            }
        }
        List<TourPlaceRequest.Input> places = objectMapper.readValue(inputPath.toFile(), new TypeReference<>() { });
        List<TourPlaceResponse.MatchResult> previous = objectMapper.readValue(tourPath.toFile(), new TypeReference<>() { });
        Map<String, TourPlaceResponse.MatchResult> byId = index(places, previous);
        Files.createDirectories(outputPath.getParent());
        List<Result> results = new ArrayList<>();
        if (places.isEmpty()) {
            save(outputPath, results);
        }
        int requests = 0;
        for (TourPlaceRequest.Input place : places) {
            TourPlaceResponse.MatchResult tour = byId.get(place.externalPlaceId());
            // 기존 TourAPI 설명 우선 사용과 설명 누락 장소의 요청 한도 적용
            if (StringUtils.hasText(tour.overview())) {
                results.add(new Result(place.externalPlaceId(), place.name(), tour.overview(), TOUR_API,
                        COMPLETED, tour, null));
            } else if (requests >= maxRequests) {
                results.add(new Result(place.externalPlaceId(), place.name(), null, null,
                        SKIPPED_LIMIT, tour, null));
            } else {
                requests++;
                GeminiPlaceResponse.Result gemini = enrichmentService.enrich(place);
                boolean usable = gemini.matchStatus() == VERIFIED
                        && StringUtils.hasText(gemini.overview());
                results.add(new Result(place.externalPlaceId(), place.name(), usable ? gemini.overview() : null,
                        usable ? DAUM_GEMINI : null, COMPLETED, tour, gemini));
            }
            save(outputPath, results);
        }
        log.info("Gemini place enrichment completed. count={}, requests={}, output={}", results.size(), requests, outputPath);
    }

    // 외부 요청 전 입력 장소와 TourAPI 결과의 전체 연결 검증
    private Map<String, TourPlaceResponse.MatchResult> index(List<TourPlaceRequest.Input> places,
                                                            List<TourPlaceResponse.MatchResult> previous) {
        Map<String, TourPlaceResponse.MatchResult> byId = new HashMap<>();
        for (TourPlaceResponse.MatchResult result : previous) {
            if (result == null || !StringUtils.hasText(result.externalPlaceId())
                    || byId.putIfAbsent(result.externalPlaceId(), result) != null) {
                throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_RESPONSE);
            }
        }
        Set<String> ids = new HashSet<>();
        for (TourPlaceRequest.Input place : places) {
            if (place == null || !StringUtils.hasText(place.externalPlaceId()) || !StringUtils.hasText(place.name())
                    || !ids.add(place.externalPlaceId()) || !byId.containsKey(place.externalPlaceId())) {
                throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_RESPONSE);
            }
            if (!StringUtils.hasText(byId.get(place.externalPlaceId()).overview())
                    && !StringUtils.hasText(place.roadAddress()) && !StringUtils.hasText(place.lotAddress())) {
                throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_ENRICHMENT_INPUT_REQUIRED);
            }
        }
        return byId;
    }

    // 부분 기록 방지를 위한 임시 파일 작성 후 원자적 교체
    private void save(Path outputPath, List<Result> results) throws IOException {
        Path temporary = Files.createTempFile(outputPath.getParent(), ".gemini-enrichment-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), results);
            Files.move(temporary, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

package com.yeogido.backend.domain.placeenrichment.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.placeenrichment.client.DaumSearchClient;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.domain.placeenrichment.client.GeminiSearchClient;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.dto.response.DaumSearchResponse;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse.SelectedSentence;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse.Selection;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.enums.GeminiPlaceMatchStatus;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceFactCategory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.yeogido.backend.domain.placeenrichment.enums.GeminiPlaceMatchStatus.*;
import static com.yeogido.backend.domain.placeenrichment.service.PlaceSourceIdentityVerifier.containsAddress;

@Service
public class GeminiPlaceEnrichmentServiceImpl implements GeminiPlaceEnrichmentService {

    private static final String REVIEW_ATTRIBUTION = "방문 후기에 따르면";
    private static final Pattern ATMOSPHERE_EVIDENCE_PATTERN =
            Pattern.compile("(?s).*(분위기|아늑|조용|차분|고즈넉|활기).*");
    private static final Pattern OVERVIEW_PREFIX_PATTERN =
            Pattern.compile("^(?:(?:" + REVIEW_ATTRIBUTION + "|검색 자료에 따르면|또한)\\s*)+");

    private final GeminiSearchClient client;
    private final DaumSearchClient searchClient;
    private final ObjectMapper objectMapper;
    private final String instructions;

    public GeminiPlaceEnrichmentServiceImpl(GeminiSearchClient client, DaumSearchClient searchClient, ObjectMapper objectMapper) throws IOException {
        this.client = client;
        this.searchClient = searchClient;
        this.objectMapper = objectMapper;
        this.instructions = new ClassPathResource("prompts/gemini-place-overview.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public GeminiPlaceResponse.Result enrich(TourPlaceRequest.Input input) {
        if (input == null || !StringUtils.hasText(input.externalPlaceId()) || !StringUtils.hasText(input.name())
                || !StringUtils.hasText(input.roadAddress()) && !StringUtils.hasText(input.lotAddress())) {
            throw new GeneralException(PlaceEnrichmentErrorCode.GEMINI_ENRICHMENT_INPUT_REQUIRED);
        }
        try {
            List<DaumSearchResponse.SearchSource> searchSources = searchClient.search(input);
            JsonNode metadata = objectMapper.valueToTree(Map.of("provider", "DAUM", "retrievalMode", "SEARCH_SNIPPETS",
                    "searchSources", searchSources));
            if (searchSources.isEmpty()) {
                return new GeminiPlaceResponse.Result(input.externalPlaceId(), input.name(), NOT_FOUND,
                        "Daum 검색에서 자료를 찾지 못했습니다.", null, List.of(), List.of(), List.of(), metadata, List.of());
            }
            Map<String, Evidence> evidenceCatalog = evidenceCatalog(searchSources);
            Set<String> descriptiveSourceIds = searchSources.stream()
                    .filter(source -> PlaceSourceIdentityVerifier.matches(input, source))
                    .map(DaumSearchResponse.SearchSource::sourceId).collect(Collectors.toSet());
            ((com.fasterxml.jackson.databind.node.ObjectNode) metadata).set("descriptiveSourceIds",
                    objectMapper.valueToTree(searchSources.stream().map(DaumSearchResponse.SearchSource::sourceId)
                            .filter(descriptiveSourceIds::contains).toList()));
            JsonNode response = client.search(instructions,
                    objectMapper.writeValueAsString(Map.of("place", input, "evidenceCatalog", evidenceCatalog.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Map.of("text", entry.getValue().text(), "descriptiveAllowed", descriptiveSourceIds.contains(entry.getValue().sourceId()), "sourceType", searchSources.stream().filter(source -> source.sourceId().equals(entry.getValue().sourceId())).findFirst().orElseThrow().sourceType()))), "atmosphereCandidates", evidenceCatalog.entrySet().stream()
                            .filter(entry -> descriptiveSourceIds.contains(entry.getValue().sourceId()))
                            .filter(entry -> ATMOSPHERE_EVIDENCE_PATTERN.matcher(entry.getValue().text()).matches())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))));
            JsonNode candidates = response == null ? null : response.path("candidates");
            require(candidates != null && candidates.isArray() && candidates.size() == 1);
            JsonNode candidate = candidates.get(0);
            require("STOP".equals(candidate.path("finishReason").asText()));
            JsonNode parts = candidate.path("content").path("parts");
            require(parts.isArray() && !parts.isEmpty());
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                    text.append(part.path("text").textValue());
                }
            }
            // 중복 필드와 후행 JSON 차단을 위한 엄격한 응답 파싱
            JsonNode output = objectMapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text.toString());
            validateShape(output);
            Selection selection = objectMapper.readerFor(Selection.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(output);
            Payload payload = resolve(input, selection, evidenceCatalog, searchSources);
            validate(input, payload, searchSources);
            return new GeminiPlaceResponse.Result(payload.id(), payload.name(), payload.matchStatus(),
                    payload.matchReason(), payload.overview(), payload.sources(), payload.sentenceSources(),
                    payload.conflicts(), metadata.deepCopy(), payload.facts());
        } catch (IOException exception) {
            throw invalidResponse();
        }
    }

    private static void validateShape(JsonNode output) {
        require(output != null && output.isObject());
        requireText(output, "id", "name", "matchStatus", "matchReason");
        require(output.has("overview") && (output.path("overview").isNull() || output.path("overview").isTextual()));
        for (String field : List.of("matchEvidenceIds", "sentenceSources", "conflicts", "facts")) {
            require(output.path(field).isArray());
        }
        for (JsonNode fact : output.path("facts")) {
            requireText(fact, "category", "value", "evidenceId");
            require(!fact.has("subject") || fact.path("subject").isNull() || fact.path("subject").isTextual());
            require(fact.has("scope") && (fact.path("scope").isNull() || fact.path("scope").isTextual()));
        }
        output.path("matchEvidenceIds").forEach(reference -> require(reference.isTextual()));
        for (JsonNode sentence : output.path("sentenceSources")) {
            requireText(sentence, "sentence");
            requireStringReferences(sentence);
        }
        for (JsonNode conflict : output.path("conflicts")) {
            requireText(conflict, "field", "description");
            requireStringReferences(conflict);
        }
    }

    // 모델의 근거 식별자 선택과 서버의 원문 발췌 연결
    private Map<String, Evidence> evidenceCatalog(List<DaumSearchResponse.SearchSource> sources) {
        Map<String, Evidence> catalog = new java.util.LinkedHashMap<>();
        for (var source : sources) {
            java.util.List<String> excerpts = new java.util.ArrayList<>();
            excerpts.add(source.title());
            excerpts.addAll(java.util.Arrays.asList(source.snippet().split("\\n\\[별도 검색 발췌\\]\\n", -1)));
            for (String excerpt : excerpts) {
                // 항목명과 설명의 문맥 유지를 위한 검색 발췌 단위 보존
                if (StringUtils.hasText(excerpt)) {
                    catalog.put("E" + (catalog.size() + 1), new Evidence(source.sourceId(), excerpt));
                }
            }
        }
        return catalog;
    }

    private Payload resolve(TourPlaceRequest.Input input, Selection selection, Map<String, Evidence> catalog,
                            List<DaumSearchResponse.SearchSource> searchSources) {
        require(selection != null && selection.matchEvidenceIds() != null && selection.facts() != null
                && selection.sentenceSources() != null && selection.conflicts() != null);
        Map<String, DaumSearchResponse.SearchSource> supplied = searchSources.stream()
                .collect(Collectors.toMap(DaumSearchResponse.SearchSource::sourceId, source -> source));
        Set<String> descriptiveSourceIds = searchSources.stream()
                .filter(source -> PlaceSourceIdentityVerifier.matches(input, source))
                .map(DaumSearchResponse.SearchSource::sourceId).collect(Collectors.toSet());
        // 사실과 설명에 연결된 모든 출처를 서버에서 독립적으로 검증한다.
        selection.facts().forEach(fact -> require(descriptiveSourceIds.contains(
                catalog.containsKey(fact.evidenceId()) ? catalog.get(fact.evidenceId()).sourceId() : null)));
        selection.sentenceSources().forEach(sentence -> {
            require(sentence.evidenceIds() != null && !sentence.evidenceIds().isEmpty()
                    && sentence.evidenceIds().stream().allMatch(catalog::containsKey));
            sentence.evidenceIds().forEach(reference -> require(
                    descriptiveSourceIds.contains(catalog.get(reference).sourceId())));
        });
        List<String> references = new java.util.ArrayList<>(selection.matchEvidenceIds());
        selection.facts().forEach(fact -> references.add(fact.evidenceId()));
        selection.sentenceSources().forEach(sentence -> references.addAll(sentence.evidenceIds()));
        selection.conflicts().forEach(conflict -> {
            validateReferences(conflict.evidenceIds(), catalog.keySet(), 2);
            references.addAll(conflict.evidenceIds());
        });
        Map<String, GeminiPlaceResponse.Source> resolved = new java.util.LinkedHashMap<>();
        for (String reference : references) {
            Evidence evidence = catalog.get(reference);
            require(evidence != null);
            DaumSearchResponse.SearchSource original = supplied.get(evidence.sourceId());
            require(original != null);
            resolved.putIfAbsent(original.sourceId(), new GeminiPlaceResponse.Source(original.sourceId(),
                    original.url(), original.sourceType(), evidence.text(), original.collectedAt()));
        }
        // 장소 식별에 사용된 출처의 입력 주소 포함 여부 검증
        require(selection.matchStatus() != VERIFIED || selection.matchEvidenceIds().stream()
                .map(catalog::get).map(evidence -> supplied.get(evidence.sourceId()))
                .anyMatch(source -> containsAddress(source.title() + " " + source.snippet(), input.roadAddress())
                        || containsAddress(source.title() + " " + source.snippet(), input.lotAddress())));
        List<GeminiPlaceResponse.Source> sources = List.copyOf(resolved.values());
        List<GeminiPlaceResponse.Fact> facts = selection.facts().stream().map(fact -> {
            Evidence evidence = catalog.get(fact.evidenceId());
            require(evidence != null);
            if (fact.category() == PlaceFactCategory.ATMOSPHERE) {
                require(StringUtils.hasText(fact.scope()));
            }
            return new GeminiPlaceResponse.Fact(fact.category(), fact.value(), fact.scope(), evidence.text(), evidence.sourceId());
        }).toList();
        // 후기 출처 표시 추가 전 생성 설명과 근거 문장 간 일치 검증
        if (selection.overview() != null) {
            require(normalizeWhitespace(selection.overview()).equals(normalizeWhitespace(selection.sentenceSources().stream()
                    .map(SelectedSentence::sentence).collect(Collectors.joining(" ")))));
        }
        Set<String> reviews = sources.stream().filter(source -> source.sourceType() == PlaceEvidenceSourceType.REVIEW)
                .map(GeminiPlaceResponse.Source::sourceId).collect(Collectors.toSet());
        List<GeminiPlaceResponse.SentenceSource> sentences = selection.sentenceSources().stream().map(sentence -> {
            require(sentence.evidenceIds() != null && StringUtils.hasText(sentence.sentence()));
            List<String> sourceIds = resolveReferences(sentence.evidenceIds(), catalog);
            String text = sentence.sentence();
            text = OVERVIEW_PREFIX_PATTERN.matcher(text).replaceFirst("");
            if (sourceIds.stream().anyMatch(reviews::contains) && !text.startsWith(REVIEW_ATTRIBUTION)) {
                text = REVIEW_ATTRIBUTION + " " + text;
            }
            return new GeminiPlaceResponse.SentenceSource(text, sourceIds);
        }).toList();
        String overview = selection.overview() == null ? null : sentences.stream()
                .map(GeminiPlaceResponse.SentenceSource::sentence).collect(Collectors.joining(" "));
        return new Payload(selection.id(), selection.name(), selection.matchStatus(), selection.matchReason(), overview,
                sources, sentences, selection.conflicts().stream().map(conflict -> new GeminiPlaceResponse.Conflict(
                        conflict.field(), conflict.description(), resolveReferences(conflict.evidenceIds(), catalog))).toList(), facts);
    }

    private static List<String> resolveReferences(List<String> references, Map<String, Evidence> catalog) {
        require(references != null);
        return references.stream().map(reference -> {
            Evidence evidence = catalog.get(reference);
            require(evidence != null);
            return evidence.sourceId();
        }).distinct().toList();
    }

    private static void requireText(JsonNode object, String... fields) {
        for (String field : fields) {
            require(object.path(field).isTextual());
        }
    }

    private static void requireStringReferences(JsonNode object) {
        require(object.path("evidenceIds").isArray());
        object.path("evidenceIds").forEach(reference -> require(reference.isTextual()));
    }

    // 원본 출처 무결성과 상태별 설명 허용 조건 검증
    private void validate(TourPlaceRequest.Input input, Payload payload, List<DaumSearchResponse.SearchSource> searchSources) {
        require(payload != null && Objects.equals(input.externalPlaceId(), payload.id())
                && Objects.equals(input.name(), payload.name()) && payload.matchStatus() != null
                && StringUtils.hasText(payload.matchReason()) && payload.sources() != null
                && payload.sentenceSources() != null && payload.conflicts() != null && payload.facts() != null);
        require(payload.matchStatus() == VERIFIED || payload.overview() == null);
        Map<String, DaumSearchResponse.SearchSource> supplied = searchSources.stream()
                .collect(Collectors.toMap(DaumSearchResponse.SearchSource::sourceId, source -> source));
        Set<String> sourceIds = new HashSet<>();
        for (GeminiPlaceResponse.Source source : payload.sources()) {
            require(source != null && StringUtils.hasText(source.sourceId())
                    && sourceIds.add(source.sourceId()) && source.sourceType() != null
                    && StringUtils.hasText(source.evidence()) && validUrl(source.url())
                    && supplied.containsKey(source.sourceId()));
            DaumSearchResponse.SearchSource original = supplied.get(source.sourceId());
            require(original.url().equals(source.url()) && original.sourceType() == source.sourceType()
                    && literalEvidence(original, source.evidence()));
        }
        require(payload.matchStatus() != VERIFIED || !sourceIds.isEmpty()
                && payload.sources().stream().map(source -> supplied.get(source.sourceId()))
                .anyMatch(source -> containsAddress(source.title() + " " + source.snippet(), input.roadAddress())
                        || containsAddress(source.title() + " " + source.snippet(), input.lotAddress())));
        require(payload.matchStatus() != NOT_FOUND || sourceIds.isEmpty() && payload.conflicts().isEmpty());
        for (GeminiPlaceResponse.Conflict conflict : payload.conflicts()) {
            require(conflict != null && StringUtils.hasText(conflict.field())
                    && StringUtils.hasText(conflict.description()));
            validateReferences(conflict.sourceIds(), sourceIds, 1);
        }
        require(payload.matchStatus() == VERIFIED || payload.facts().isEmpty());
        for (GeminiPlaceResponse.Fact fact : payload.facts()) {
            require(fact != null && fact.category() != null && StringUtils.hasText(fact.value())
                    && StringUtils.hasText(fact.evidence()) && sourceIds.contains(fact.sourceId()));
            DaumSearchResponse.SearchSource original = supplied.get(fact.sourceId());
            require(literalEvidence(original, fact.evidence()));
            require(!fact.value().contains("추천"));
            if (fact.category() == PlaceFactCategory.ACTIVITIES) {
                require(!fact.value().matches("(?s).*(바로 앞|인근|주변|근처).*"));
            }
        }
        if (payload.overview() == null) {
            require(payload.sentenceSources().isEmpty());
            return;
        }
        require(StringUtils.hasText(payload.overview()) && !payload.overview().matches("(?s).*<[^>]+>.*")
                && !payload.sentenceSources().isEmpty() && !payload.facts().isEmpty());
        for (GeminiPlaceResponse.SentenceSource sentence : payload.sentenceSources()) {
            require(sentence != null && StringUtils.hasText(sentence.sentence()));
            validateReferences(sentence.sourceIds(), sourceIds, 1);
            if (sentence.sourceIds().stream().anyMatch(id -> supplied.get(id).sourceType()
                    == PlaceEvidenceSourceType.REVIEW)) {
                require(sentence.sentence().startsWith(REVIEW_ATTRIBUTION));
            }
        }
        String reconstructed = payload.sentenceSources().stream().map(GeminiPlaceResponse.SentenceSource::sentence)
                .collect(Collectors.joining(" "));
        require(normalizeWhitespace(payload.overview()).equals(normalizeWhitespace(reconstructed)));
    }

    private static boolean literalEvidence(DaumSearchResponse.SearchSource source, String evidence) {
        if (evidence.contains("[별도 검색 발췌]")) return false;
        return source.title().contains(evidence) || java.util.Arrays.stream(
                source.snippet().split("\\n\\[별도 검색 발췌\\]\\n", -1)).anyMatch(part -> part.contains(evidence));
    }

    private static void validateReferences(List<String> references, Set<String> sourceIds, int minimum) {
        require(references != null && references.size() >= minimum
                && new HashSet<>(references).size() == references.size()
                && references.stream().allMatch(sourceIds::contains));
    }

    private static boolean validUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeWhitespace(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw invalidResponse();
        }
    }

    private static GeneralException invalidResponse() {
        return new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_RESPONSE);
    }

    private record Evidence(String sourceId, String text) { }
    private record Payload(String id, String name, GeminiPlaceMatchStatus matchStatus,
                           String matchReason, String overview, List<GeminiPlaceResponse.Source> sources,
                           List<GeminiPlaceResponse.SentenceSource> sentenceSources,
                           List<GeminiPlaceResponse.Conflict> conflicts, List<GeminiPlaceResponse.Fact> facts) { }
}

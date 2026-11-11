package com.yeogido.backend.domain.placeenrichment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import org.springframework.http.converter.HttpMessageConversionException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import lombok.extern.slf4j.Slf4j;
import com.yeogido.backend.domain.placeenrichment.dto.response.DaumSearchResponse;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

@Component
@Slf4j
public class DaumSearchClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ERROR_BODY_LENGTH = 65536;
    private static final String BLOG_SEARCH_TYPE = "blog";
    private static final String WEB_SEARCH_TYPE = "web";
    private static final String SETTING_SEARCH_KEYWORDS = " 공간 좌석 실내 야외 분위기";
    private final RestClient client;
    private final String apiKey;
    private final int size;

    @Autowired
    public DaumSearchClient(RestClient.Builder builder,
                            @Value("${app.daum.api-key:}") String apiKey,
                            @Value("${app.daum.base-url:https://dapi.kakao.com}") String baseUrl,
                            @Value("${app.daum.timeout-seconds:15}") int timeoutSeconds,
                            @Value("${app.daum.search-size:5}") int size) {
        if (timeoutSeconds < 1 || timeoutSeconds > 120 || size < 1 || size > 10) {
            throw configurationFailure();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.size = size;
    }

    DaumSearchClient(RestClient client, String apiKey, int size) {
        this.client = client;
        this.apiKey = apiKey;
        this.size = size;
    }

    public List<DaumSearchResponse.SearchSource> search(TourPlaceRequest.Input input) {
        String address = StringUtils.hasText(input.roadAddress()) ? input.roadAddress() : input.lotAddress();
        String query = input.name() + " " + address;
        Map<String, DaumSearchResponse.SearchSource> sources = new LinkedHashMap<>();
        Map<String, Set<String>> snippetsByUrl = new LinkedHashMap<>();
        // 장소 식별 후 활동과 공간 설명 보완을 위한 최대 네 차례 검색
        List<SearchQuery> queries = List.of(new SearchQuery(BLOG_SEARCH_TYPE, query), new SearchQuery(WEB_SEARCH_TYPE, query),
                new SearchQuery(BLOG_SEARCH_TYPE, query + activityKeywords(input.categoryGroupCode())),
                new SearchQuery(BLOG_SEARCH_TYPE, query + SETTING_SEARCH_KEYWORDS));
        for (SearchQuery searchQuery : queries) {
            String operation = BLOG_SEARCH_TYPE.equals(searchQuery.type()) ? "searchBlog" : "searchWeb";
            JsonNode response = PlaceOverviewApiScope.call("daum", operation,
                    () -> request(searchQuery, operation));
            Instant collectedAt = Instant.now();
            for (JsonNode document : response.path("documents")) {
                String url = document.path("url").asText();
                String title = plainText(document.path("title").asText());
                String snippet = plainText(document.path("contents").asText());
                if (StringUtils.hasText(url) && (StringUtils.hasText(title) || StringUtils.hasText(snippet))) {
                    DaumSearchResponse.SearchSource existing = sources.get(url);
                    Set<String> snippets = snippetsByUrl.computeIfAbsent(url, ignored -> new LinkedHashSet<>());
                    if (StringUtils.hasText(snippet) && snippets.size() < 4) {
                        snippets.add(snippet);
                    }
                    // 검색 발췌 원문 보존과 발췌 간 경계 구분
                    String combined = String.join("\n[별도 검색 발췌]\n", snippets);
                    sources.put(url, existing == null
                            ? new DaumSearchResponse.SearchSource("S" + (sources.size() + 1), url,
                                    BLOG_SEARCH_TYPE.equals(searchQuery.type()) ? PlaceEvidenceSourceType.REVIEW : PlaceEvidenceSourceType.OTHER, title, combined, collectedAt)
                            : new DaumSearchResponse.SearchSource(existing.sourceId(), existing.url(), existing.sourceType(), existing.title(), combined, existing.collectedAt()));
                }
            }
        }
        return List.copyOf(sources.values());
    }

    private JsonNode request(SearchQuery query, String operation) {
        if (!StringUtils.hasText(apiKey)) throw configurationFailure();
        try {
            JsonNode response = client.get().uri(builder -> builder.path("/v2/search/" + query.type())
                            .queryParam("query", "{query}").queryParam("size", size).build(query.query()))
                    .header("Authorization", "KakaoAK " + apiKey).retrieve().body(JsonNode.class);
            if (response == null || !response.path("documents").isArray()) {
                logFailure(query.type(), "response_parse_error", null);
                throw new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
                        "daum", operation, ExternalApiFailureKind.INVALID_RESPONSE, null, null, null);
            }
            return response;
        } catch (RestClientResponseException exception) {
            ExternalApiFailure failure = ExternalApiFailure.http(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
                    "daum", operation, exception);
            if (exception.getStatusCode().value() == 400 && isQuotaExceeded(exception)) {
                failure = new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
                        "daum", operation, ExternalApiFailureKind.DAILY_QUOTA, 400, "-10", failure.getRetryAfter());
            }
            logFailure(query.type(), failure.failureType(), exception.getStatusCode().value());
            throw failure;
        } catch (RestClientException | HttpMessageConversionException exception) {
            logFailure(query.type(), SafeLogDetails.failureType(exception), null);
            throw ExternalApiFailure.client(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
                    "daum", operation, exception);
        }
    }

    private static boolean isQuotaExceeded(RestClientResponseException exception) {
        byte[] body = exception.getResponseBodyAsByteArray();
        if (body.length == 0 || body.length > MAX_ERROR_BODY_LENGTH) return false;
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode code = root == null ? null : root.get("code");
            return code != null && code.isIntegralNumber() && code.canConvertToInt() && code.intValue() == -10;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static ExternalApiFailure configurationFailure() {
        return new ExternalApiFailure(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG,
                "daum", "searchBlog", ExternalApiFailureKind.CONFIGURATION, null, null, null);
    }

    private void logFailure(String searchType, String failureType, Integer status) {
        var entry = log.atWarn()
                .addKeyValue("event", "external_api.call.failed")
                .addKeyValue("provider", "daum")
                .addKeyValue("operation", BLOG_SEARCH_TYPE.equals(searchType) ? "searchBlog" : "searchWeb")
                .addKeyValue("failureType", failureType)
                .addKeyValue("errorCode", PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR.getCode())
                .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, status));
        if (status != null) {
            entry.addKeyValue("upstreamHttpStatus", status);
        }
        entry.log("External API call failed");
    }

    private static String activityKeywords(String category) {
        if ("CE7".equals(category)) return " 메뉴 음료 디저트";
        if ("FD6".equals(category)) return " 메뉴 음식";
        if ("CT1".equals(category)) return " 전시 관람 체험";
        return " 주요 활동 체험";
    }

    private static String plainText(String text) {
        return HtmlUtils.htmlUnescape(text.replaceAll("<[^>]+>", ""));
    }

    private record SearchQuery(String type, String query) { }

}

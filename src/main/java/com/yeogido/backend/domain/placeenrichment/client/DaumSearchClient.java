package com.yeogido.backend.domain.placeenrichment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.domain.placeenrichment.dto.response.DaumSearchResponse;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;

import java.time.Duration;
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
public class DaumSearchClient {
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
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
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
        if (!StringUtils.hasText(apiKey)) {
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
        }
        String address = StringUtils.hasText(input.roadAddress()) ? input.roadAddress() : input.lotAddress();
        String query = input.name() + " " + address;
        Map<String, DaumSearchResponse.SearchSource> sources = new LinkedHashMap<>();
        Map<String, Set<String>> snippetsByUrl = new LinkedHashMap<>();
        // 장소 식별 후 활동과 공간 설명 보완을 위한 최대 네 차례 검색
        List<SearchQuery> queries = List.of(new SearchQuery(BLOG_SEARCH_TYPE, query), new SearchQuery(WEB_SEARCH_TYPE, query),
                new SearchQuery(BLOG_SEARCH_TYPE, query + activityKeywords(input.categoryGroupCode())),
                new SearchQuery(BLOG_SEARCH_TYPE, query + SETTING_SEARCH_KEYWORDS));
        for (SearchQuery searchQuery : queries) {
            JsonNode response;
            try {
                response = client.get().uri(builder -> builder.path("/v2/search/" + searchQuery.type())
                                .queryParam("query", "{query}").queryParam("size", size).build(searchQuery.query()))
                        .header("Authorization", "KakaoAK " + apiKey).retrieve().body(JsonNode.class);
            } catch (RestClientResponseException exception) {
                throw new GeneralException(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);
            } catch (RestClientException exception) {
                throw new GeneralException(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);
            }
            if (response == null || !response.path("documents").isArray()) {
                throw new GeneralException(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR);
            }
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
                                    BLOG_SEARCH_TYPE.equals(searchQuery.type()) ? PlaceEvidenceSourceType.REVIEW : PlaceEvidenceSourceType.OTHER, title, combined)
                            : new DaumSearchResponse.SearchSource(existing.sourceId(), existing.url(), existing.sourceType(), existing.title(), combined));
                }
            }
        }
        return List.copyOf(sources.values());
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

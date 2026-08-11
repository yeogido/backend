package com.yeogido.backend.domain.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class TourApiClient {

    private static final DateTimeFormatter TOUR_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient.Builder restClientBuilder;

    @Value("${app.tour-api.base-url}")
    private String baseUrl;

    @Value("${app.tour-api.service-key}")
    private String serviceKey;

    @Value("${app.tour-api.mobile-os:ETC}")
    private String mobileOs;

    @Value("${app.tour-api.mobile-app:Yeogido}")
    private String mobileApp;

    public JsonNode searchKeyword(
            String keyword,
            Integer areaCode,
            int page,
            int size
    ) {
        return execute(
                "/searchKeyword2",
                uriBuilder -> {
                    var builder = commonQuery(uriBuilder)
                            .queryParam("keyword", keyword)
                            .queryParam("contentTypeId", 15)
                            .queryParam("arrange", "A")
                            .queryParam("pageNo", page)
                            .queryParam("numOfRows", size);

                    if (areaCode != null) {
                        builder.queryParam("areaCode", areaCode);
                    }

                    return builder.build();
                }
        );
    }

    public JsonNode searchFestivals(
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        return execute(
                "/searchFestival2",
                uriBuilder -> commonQuery(uriBuilder)
                        .queryParam("eventStartDate", startDate.format(TOUR_DATE_FORMATTER))
                        .queryParam("eventEndDate", endDate.format(TOUR_DATE_FORMATTER))
                        .queryParam("arrange", "A")
                        .queryParam("pageNo", page)
                        .queryParam("numOfRows", size)
                        .build()
        );
    }

    public JsonNode detailCommon(String contentId) {
        return execute(
                "/detailCommon2",
                uriBuilder -> commonQuery(uriBuilder)
                        .queryParam("contentId", contentId)
                        .build()
        );
    }

    public JsonNode detailIntro(String contentId, String contentTypeId) {
        return execute(
                "/detailIntro2",
                uriBuilder -> commonQuery(uriBuilder)
                        .queryParam("contentId", contentId)
                        .queryParam("contentTypeId", contentTypeId)
                        .build()
        );
    }

    public JsonNode detailImages(String contentId) {
        return execute(
                "/detailImage2",
                uriBuilder -> commonQuery(uriBuilder)
                        .queryParam("contentId", contentId)
                        .queryParam("imageYN", "Y")
                        .queryParam("subImageYN", "Y")
                        .queryParam("numOfRows", 30)
                        .queryParam("pageNo", 1)
                        .build()
        );
    }

    public List<JsonNode> items(JsonNode response) {
        JsonNode item = response.path("response")
                .path("body")
                .path("items")
                .path("item");

        if (item.isArray()) {
            return item.valueStream().toList();
        }

        if (item.isObject()) {
            return List.of(item);
        }

        return List.of();
    }

    public int totalCount(JsonNode response) {
        return response.path("response")
                .path("body")
                .path("totalCount")
                .asInt(0);
    }

    private JsonNode execute(
            String path,
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction
    ) {
        if (!StringUtils.hasText(serviceKey)) {
            throw new GeneralException(ContentErrorCode.TOUR_API_NOT_CONFIGURED);
        }

        try {
            JsonNode response = restClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri(path, uriFunction)
                    .retrieve()
                    .body(JsonNode.class);

            validateResponse(response);
            return response;
        } catch (GeneralException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Tour API request failed. path={}", path, exception);
            throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
        }
    }

    private org.springframework.web.util.UriBuilder commonQuery(
            org.springframework.web.util.UriBuilder uriBuilder
    ) {
        return uriBuilder
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json");
    }

    private void validateResponse(JsonNode response) {
        String resultCode = response == null
                ? null
                : response.path("response").path("header").path("resultCode").asText();

        if (!"0000".equals(resultCode)) {
            log.warn("Tour API returned invalid response. resultCode={}", resultCode);
            throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
        }
    }
}

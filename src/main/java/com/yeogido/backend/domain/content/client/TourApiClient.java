package com.yeogido.backend.domain.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class TourApiClient {

    private static final DateTimeFormatter TOUR_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient.Builder restClientBuilder;

    public TourApiClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder.clone();
    }

    @Value("${app.tour-api.base-url}")
    private String baseUrl;

    @Value("${app.tour-api.service-key}")
    private String serviceKey;

    @Value("${app.tour-api.mobile-os:ETC}")
    private String mobileOs;

    @Value("${app.tour-api.mobile-app:Yeogido}")
    private String mobileApp;

    @PostConstruct
    void configureTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(15));
        restClientBuilder.requestFactory(factory);
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

    public JsonNode legalDistrictCodes(String provinceCode, int page, int size) {
        return execute(
                "/ldongCode2",
                uriBuilder -> {
                    commonQuery(uriBuilder)
                            .queryParam("lDongListYn", "N")
                            .queryParam("pageNo", page)
                            .queryParam("numOfRows", size);
                    if (StringUtils.hasText(provinceCode)) {
                        uriBuilder.queryParam("lDongRegnCd", provinceCode);
                    }
                    return uriBuilder.build();
                }
        );
    }

    public JsonNode searchPlaces(String keyword, String provinceCode, String districtCode, int page, int size) {
        return execute(
                "/searchKeyword2",
                uriBuilder -> {
                    commonQuery(uriBuilder)
                            .queryParam("keyword", "{keyword}")
                            .queryParam("arrange", "A")
                            .queryParam("pageNo", page)
                            .queryParam("numOfRows", size);
                    if (StringUtils.hasText(provinceCode)) {
                        uriBuilder.queryParam("lDongRegnCd", provinceCode);
                        if (StringUtils.hasText(districtCode)) {
                            uriBuilder.queryParam("lDongSignguCd", districtCode);
                        }
                    }
                    return uriBuilder.build(keyword);
                }
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

            validateResponse(response, path);
            return response;
        } catch (GeneralException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            failureLog(path, SafeLogDetails.httpFailureType(status))
                    .addKeyValue("upstreamHttpStatus", status)
                    .addKeyValue("retryable", SafeLogDetails.isRetryable(null, status))
                    .log("Tour API request failed.");
            throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
        } catch (RestClientException exception) {
            String failureType = SafeLogDetails.failureType(exception);
            failureLog(path, failureType)
                    .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, null))
                    .log("Tour API request failed.");
            throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
        }
    }

    private org.slf4j.spi.LoggingEventBuilder failureLog(String operation, String failureType) {
        return log.atWarn()
                .addKeyValue("event", "external_api.call.failed")
                .addKeyValue("provider", "tour")
                .addKeyValue("operation", operation)
                .addKeyValue("failureType", failureType)
                .addKeyValue("errorCode", ContentErrorCode.TOUR_API_ERROR.getCode());
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

    private void validateResponse(JsonNode response, String path) {
        String resultCode = response == null
                ? null
                : response.path("response").path("header").path("resultCode").asText();

        if (!"0000".equals(resultCode)) {
            // 외부 응답 데이터의 로그 유입 방지를 위한 오류 코드 형식 제한
            String safeResultCode = resultCode == null || resultCode.isBlank() ? "missing"
                    : resultCode.matches("[0-9]{4}") ? resultCode : "invalid";
            failureLog(path, "invalid_response")
                    .addKeyValue("resultCode", safeResultCode)
                    .addKeyValue("retryable", false)
                    .log("Tour API returned invalid response.");
            throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
        }
    }
}

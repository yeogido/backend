package com.yeogido.backend.domain.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import com.yeogido.backend.domain.placeenrichment.client.PlaceOverviewApiScope;
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
import org.springframework.http.converter.HttpMessageConversionException;
import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class TourApiClient {

    private static final DateTimeFormatter TOUR_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ERROR_BODY_LENGTH = 65536;
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
        return PlaceOverviewApiScope.call("tour", path, () -> request(path, uriFunction));
    }

    private JsonNode request(String path,
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction) {
        if (!StringUtils.hasText(serviceKey)) {
            throw new ExternalApiFailure(ContentErrorCode.TOUR_API_NOT_CONFIGURED,
                    "tour", path, ExternalApiFailureKind.CONFIGURATION, null, null, null);
        }
        try {
            String body = restClientBuilder.baseUrl(baseUrl).build().get().uri(path, uriFunction)
                    .retrieve().body(String.class);
            // HTTP 상태보다 공급자의 명시적인 오류 코드 우선 적용 (JSON/XML 공통)
            String reasonCode = errorReasonCode(body);
            if (reasonCode != null) {
                throw responseFailure(path, reasonCode, providerFailureKind(reasonCode));
            }
            JsonNode response;
            try {
                response = body == null ? null : JSON.readTree(body);
            } catch (JsonProcessingException exception) {
                throw responseFailure(path, reasonCode, ExternalApiFailureKind.INVALID_RESPONSE, "response_parse_error");
            }
            validateResponse(response, path);
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            ExternalApiFailure failure = ExternalApiFailure.http(ContentErrorCode.TOUR_API_ERROR,
                    "tour", path, exception);
            String reasonCode = errorReasonCode(exception.getResponseBodyAsString());
            ExternalApiFailureKind providerKind = providerFailureKind(reasonCode);
            // 알 수 없는 본문 코드는 신뢰 가능한 HTTP 상태 분류를 유지
            if (providerKind != ExternalApiFailureKind.INVALID_RESPONSE) {
                failure = new ExternalApiFailure(ContentErrorCode.TOUR_API_ERROR, "tour", path,
                        providerKind, status, reasonCode, failure.getRetryAfter());
            }
            var entry = failureLog(path, providerKind == ExternalApiFailureKind.INVALID_RESPONSE
                    ? SafeLogDetails.httpFailureType(status) : failure.failureType())
                    .addKeyValue("upstreamHttpStatus", status)
                    .addKeyValue("failureKind", failure.getKind())
                    .addKeyValue("retryable", providerKind == ExternalApiFailureKind.INVALID_RESPONSE
                            ? SafeLogDetails.isRetryable(null, status) : isTransientFailure(providerKind));
            if (failure.getProviderCode() != null) entry.addKeyValue("providerCode", failure.getProviderCode());
            entry.log("Tour API request failed.");
            throw failure;
        } catch (RestClientException | HttpMessageConversionException exception) {
            String failureType = SafeLogDetails.failureType(exception);
            failureLog(path, failureType)
                    .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, null))
                    .log("Tour API request failed.");
            throw ExternalApiFailure.client(ContentErrorCode.TOUR_API_ERROR, "tour", path, exception);
        }
    }

    // TourAPI 공식 서비스 오류 코드
    private static ExternalApiFailureKind providerFailureKind(String code) {
        if (code == null) return ExternalApiFailureKind.INVALID_RESPONSE;
        return switch (code) {
            case "01" -> ExternalApiFailureKind.SERVER;
            case "05" -> ExternalApiFailureKind.TIMEOUT;
            case "20", "30", "31" -> ExternalApiFailureKind.AUTHENTICATION;
            case "22" -> ExternalApiFailureKind.DAILY_QUOTA;
            case "23" -> ExternalApiFailureKind.RATE_LIMIT;
            default -> ExternalApiFailureKind.INVALID_RESPONSE;
        };
    }

    private ExternalApiFailure responseFailure(String path, String code, ExternalApiFailureKind kind) {
        return responseFailure(path, code, kind, null);
    }

    private static boolean isTransientFailure(ExternalApiFailureKind kind) {
        return switch (kind) {
            case DAILY_QUOTA, RATE_LIMIT, TIMEOUT, CONNECTION, SERVER -> true;
            default -> false;
        };
    }

    private ExternalApiFailure responseFailure(String path, String code, ExternalApiFailureKind kind,
            String failureType) {
        ExternalApiFailure failure = new ExternalApiFailure(ContentErrorCode.TOUR_API_ERROR,
                "tour", path, kind, 200, code, null);
        String safeCode = code == null || code.isBlank() ? "missing"
                : failure.getProviderCode() == null ? "invalid" : failure.getProviderCode();
        failureLog(path, failureType == null ? failure.failureType() : failureType)
                .addKeyValue("resultCode", safeCode)
                .addKeyValue("failureKind", kind)
                .addKeyValue("upstreamHttpStatus", 200)
                .addKeyValue("retryable", isTransientFailure(kind))
                .log("Tour API returned invalid response.");
        return failure;
    }

    private org.slf4j.spi.LoggingEventBuilder failureLog(String operation, String failureType) {
        return log.atWarn()
                .addKeyValue("event", "external_api.call.failed")
                .addKeyValue("provider", "tour")
                .addKeyValue("operation", operation)
                .addKeyValue("failureType", failureType)
                .addKeyValue("errorCode", ContentErrorCode.TOUR_API_ERROR.getCode());
    }

    // 크기가 제한된 JSON, XML 오류 응답의 공급자 코드 추출
    private static String errorReasonCode(String body) {
        if (body == null || body.length() > MAX_ERROR_BODY_LENGTH) return null;
        if (body.stripLeading().startsWith("<")) return xmlReasonCode(body);
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null) return null;
            JsonNode code = root.path("returnReasonCode");
            if (code.isMissingNode()) code = root.path("cmmMsgHeader").path("returnReasonCode");
            if (code.isMissingNode()) code = root.path("OpenAPI_ServiceResponse")
                    .path("cmmMsgHeader").path("returnReasonCode");
            if (code.isMissingNode()) code = root.path("response").path("header").path("resultCode");
            String value = code.asText("");
            return value.matches("[0-9]{1,4}") && !"0000".equals(value) ? value : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String xmlReasonCode(String body) {
        var factory = XMLInputFactory.newFactory();
        // 외부 자원 접근 및 엔티티 확장 방지를 위한 DTD, 외부 엔티티 비활성화
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        try {
            var reader = factory.createXMLStreamReader(new StringReader(body));
            try {
                String code = null;
                java.util.List<String> path = new java.util.ArrayList<>();
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) return null;
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        path.add(reader.getLocalName());
                        if (path.equals(List.of("OpenAPI_ServiceResponse", "cmmMsgHeader", "returnReasonCode"))) {
                            String value = reader.getElementText().trim();
                            if (code != null || !value.matches("[0-9]{1,4}")) return null;
                            code = value;
                            path.removeLast();
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        path.removeLast();
                    }
                }
                return code;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException exception) {
            return null;
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

    private void validateResponse(JsonNode response, String path) {
        String resultCode = response == null ? null
                : response.path("response").path("header").path("resultCode").asText();
        if (!"0000".equals(resultCode)) {
            throw responseFailure(path, resultCode, providerFailureKind(resultCode));
        }
    }
}

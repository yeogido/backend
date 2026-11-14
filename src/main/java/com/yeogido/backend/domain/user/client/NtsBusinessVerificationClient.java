package com.yeogido.backend.domain.user.client;

import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NtsBusinessVerificationClient {

    @Value("${NTS_MOCK_ENABLED:false}")
    private boolean mockEnabled;

    private static final DateTimeFormatter NTS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient.Builder restClientBuilder;

    @Value("${app.nts.base-url}")
    private String baseUrl;

    @Value("${app.nts.service-key}")
    private String serviceKey;

    public NtsBusinessVerifyDTO.Result verify(
            String businessNumber,
            LocalDate openingDate,
            String representativeName
    ) {
        if (mockEnabled) {
            return mockVerifyResult(businessNumber);
        }

        NtsBusinessVerifyDTO.Request request = createRequest(
                businessNumber,
                openingDate,
                representativeName
        );

        try {
            NtsBusinessVerifyDTO.Response response =
                    restClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .post()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path("/validate")
                                            .queryParam("serviceKey", serviceKey)
                                            .queryParam("returnType", "JSON")
                                            .build()
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(NtsBusinessVerifyDTO.Response.class);

            validateResponse(response);

            return response.data().get(0);

        } catch (RestClientResponseException exception) {
            logFailure("validate", SafeLogDetails.httpFailureType(exception.getStatusCode().value()), exception.getStatusCode().value());

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );

        } catch (RestClientException exception) {
            logFailure("validate", SafeLogDetails.failureType(exception), null);

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );
        }
    }

    public NtsBusinessVerifyDTO.Status getBusinessStatus(
            String businessNumber
    ) {
        if (mockEnabled) {
            return mockStatus(businessNumber);
        }

        NtsBusinessVerifyDTO.StatusRequest request =
                new NtsBusinessVerifyDTO.StatusRequest(
                        List.of(businessNumber)
                );

        try {
            NtsBusinessVerifyDTO.StatusResponse response =
                    restClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .post()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path("/status")
                                            .queryParam("serviceKey", serviceKey)
                                            .queryParam("returnType", "JSON")
                                            .build()
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(NtsBusinessVerifyDTO.StatusResponse.class);

            validateStatusResponse(response);

            return response.data().get(0);

        } catch (RestClientResponseException exception) {
            logFailure("status", SafeLogDetails.httpFailureType(exception.getStatusCode().value()), exception.getStatusCode().value());

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );

        } catch (RestClientException exception) {
            logFailure("status", SafeLogDetails.failureType(exception), null);

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );
        }
    }

    private void validateStatusResponse(
            NtsBusinessVerifyDTO.StatusResponse response
    ) {
        if (
                response == null
                        || !"OK".equals(response.statusCode())
                        || response.data() == null
                        || response.data().isEmpty()
                        || response.data().get(0) == null
        ) {
            logFailure("status", "invalid_response", null);

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );
        }
    }

    /**
     * 로컬 Mock 진위확인 응답. 사업자번호 끝자리로 상황을 고른다.
     * <ul>
     *   <li>끝자리 8, 9: 진위확인 실패(valid "02"). 이어지는 상태 조회에서 9는 미등록, 8은 계속사업자(정보 불일치)</li>
     *   <li>그 외: 진위확인 성공(valid "01"), 영업 상태는 {@link #mockStatus(String)} 규칙</li>
     * </ul>
     */
    private NtsBusinessVerifyDTO.Result mockVerifyResult(String businessNumber) {
        char lastDigit = lastDigitOf(businessNumber);

        if (lastDigit == '8' || lastDigit == '9') {
            return new NtsBusinessVerifyDTO.Result(
                    businessNumber,
                    "02",
                    "확인할 수 없습니다.",
                    null
            );
        }

        return new NtsBusinessVerifyDTO.Result(
                businessNumber,
                "01",
                "로컬 Mock 인증 성공",
                mockStatus(businessNumber)
        );
    }

    /**
     * 로컬 Mock 영업 상태 응답. 사업자번호 끝자리로 상황을 고른다.
     * <ul>
     *   <li>끝자리 2: 휴업(02)</li>
     *   <li>끝자리 3: 폐업(03)</li>
     *   <li>끝자리 9: 국세청 미등록(빈 상태 코드)</li>
     *   <li>그 외: 계속사업자(01)</li>
     * </ul>
     */
    private NtsBusinessVerifyDTO.Status mockStatus(String businessNumber) {
        return switch (lastDigitOf(businessNumber)) {
            case '2' -> new NtsBusinessVerifyDTO.Status(
                    businessNumber,
                    "휴업자",
                    "02",
                    "부가가치세 일반과세자",
                    "01",
                    ""
            );
            case '3' -> new NtsBusinessVerifyDTO.Status(
                    businessNumber,
                    "폐업자",
                    "03",
                    "부가가치세 일반과세자",
                    "01",
                    "20240101"
            );
            case '9' -> new NtsBusinessVerifyDTO.Status(
                    businessNumber,
                    "",
                    "",
                    "국세청에 등록되지 않은 사업자등록번호입니다.",
                    "",
                    ""
            );
            default -> new NtsBusinessVerifyDTO.Status(
                    businessNumber,
                    "계속사업자",
                    "01",
                    "부가가치세 일반과세자",
                    "01",
                    ""
            );
        };
    }

    private char lastDigitOf(String businessNumber) {
        if (businessNumber == null || businessNumber.isEmpty()) {
            return '0';
        }

        return businessNumber.charAt(businessNumber.length() - 1);
    }

    private NtsBusinessVerifyDTO.Request createRequest(
            String businessNumber,
            LocalDate openingDate,
            String representativeName
    ) {
        NtsBusinessVerifyDTO.Business business =
                new NtsBusinessVerifyDTO.Business(
                        businessNumber,
                        openingDate.format(NTS_DATE_FORMATTER),
                        representativeName
                );

        return new NtsBusinessVerifyDTO.Request(
                List.of(business)
        );
    }

    private void validateResponse(
            NtsBusinessVerifyDTO.Response response
    ) {
        if (
                response == null
                        || !"OK".equals(response.statusCode())
                        || response.data() == null
                        || response.data().isEmpty()
                        || response.data().get(0) == null
        ) {
            logFailure("validate", "invalid_response", null);

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );
        }
    }
    private void logFailure(String operation, String failureType, Integer status) {
        var event = log.atWarn()
                .addKeyValue("event", "external_api.call.failed")
                .addKeyValue("provider", "nts")
                .addKeyValue("operation", operation)
                .addKeyValue("failureType", failureType)
                .addKeyValue("errorCode", BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR.getCode())
                .addKeyValue("retryable", SafeLogDetails.isRetryable(failureType, status));
        if (status != null) {
            event.addKeyValue("upstreamHttpStatus", status);
        }
        event.log("External API call failed.");
    }

}

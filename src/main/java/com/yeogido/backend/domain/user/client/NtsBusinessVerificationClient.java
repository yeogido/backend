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
            NtsBusinessVerifyDTO.Status status =
                    new NtsBusinessVerifyDTO.Status(
                            businessNumber,
                            "계속사업자",
                            "01",
                            "부가가치세 일반과세자",
                            "01",
                            ""
                    );

            return new NtsBusinessVerifyDTO.Result(
                    businessNumber,
                    "01",
                    "로컬 Mock 인증 성공",
                    status
            );
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
            return new NtsBusinessVerifyDTO.Status(
                    businessNumber,
                    "계속사업자",
                    "01",
                    "부가가치세 일반과세자",
                    "01",
                    ""
            );
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

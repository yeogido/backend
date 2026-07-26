package com.yeogido.backend.domain.user.client;

import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
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
            log.error(
                    "NTS API HTTP 오류: status={}, body={}, baseUrl={}, serviceKeyPresent={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    baseUrl,
                    serviceKey != null && !serviceKey.isBlank()
            );

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );

        } catch (RestClientException exception) {
            log.error(
                    "NTS API 연결 오류: baseUrl={}, serviceKeyPresent={}",
                    baseUrl,
                    serviceKey != null && !serviceKey.isBlank(),
                    exception
            );

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
            log.error(
                    "NTS API 비정상 응답: response={}",
                    response
            );

            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
            );
        }
    }
}
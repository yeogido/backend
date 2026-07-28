package com.yeogido.backend.domain.user.dto.nts;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class NtsBusinessVerifyDTO {

    private NtsBusinessVerifyDTO() {
    }

    public record Request(List<Business> businesses) { }

    public record Business(

            @JsonProperty("b_no")
            String businessNumber,

            @JsonProperty("start_dt")
            String openingDate,

            @JsonProperty("p_nm")
            String representativeName

    ) {
    }

    public record Response(

            @JsonProperty("status_code")
            String statusCode,

            @JsonProperty("request_cnt")
            Integer requestCount,

            @JsonProperty("valid_cnt")
            Integer validCount,

            List<Result> data

    ) {
    }

    public record Result(

            @JsonProperty("b_no")
            String businessNumber,

            String valid,

            @JsonProperty("valid_msg")
            String validMessage,

            Status status

    ) {
    }

    public record Status(

            @JsonProperty("b_no")
            String businessNumber,

            @JsonProperty("b_stt")
            String businessStatus,

            @JsonProperty("b_stt_cd")
            String businessStatusCode,

            @JsonProperty("tax_type")
            String taxType,

            @JsonProperty("tax_type_cd")
            String taxTypeCode,

            @JsonProperty("end_dt")
            String closureDate

    ) {
    }
}
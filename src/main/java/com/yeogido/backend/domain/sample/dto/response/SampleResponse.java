package com.yeogido.backend.domain.sample.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public class SampleResponse {

    @Schema(description = "Sample 상세 응답")
    public record Detail(

            @Schema(description = "Sample ID", example = "1")
            Long id,

            @Schema(description = "메시지", example = "Hello Yeogido!")
            String message

    ) {
    }

}
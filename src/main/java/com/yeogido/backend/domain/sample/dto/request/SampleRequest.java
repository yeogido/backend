package com.yeogido.backend.domain.sample.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SampleRequest {

    @Schema(description = "Sample 생성 요청")
    public record Create(

            @Schema(description = "메시지", example = "Hello Yeogido!")
            @NotBlank(message = "메시지는 필수입니다.")
            @Size(max = 50, message = "메시지는 50자 이하로 입력해주세요.")
            String message

    ) {
    }

}

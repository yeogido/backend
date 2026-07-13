package com.yeogido.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
@Schema(name = "BusinessVerifyReqDTO", description = "소상공인 사업자 인증 요청")
public record BusinessVerifyReqDTO(

    @Schema(description = "사업자 등록 번호", example = "1234567890")
    @NotBlank(message = "사업자 등록 번호는 필수입니다")
    @Pattern(regexp = "^\\d{10}$", message = "사업자 등록 번호는 숫자 10자리입니다")
    String businessNumber,

    @Schema(description = "가게명")
    @NotBlank(message = "가게명은 필수입니다")
    String shopName,

    @Schema(description = "가게 주소", example = "서울시 강남구 테헤란로 123")
    @NotBlank(message = "가게 주소는 필수입니다")
    String address
) { }

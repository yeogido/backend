package com.yeogido.backend.domain.user.dto;

import com.yeogido.backend.domain.place.dto.request.PlaceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(name = "BusinessVerifyReqDTO", description = "소상공인 사업자 인증 요청")
public record BusinessVerifyReqDTO(

        @Schema(description = "사업자등록번호", example = "1234567890")
        @NotBlank(message = "사업자등록번호는 필수입니다")
        @Pattern(
                regexp = "^\\d{10}$",
                message = "사업자등록번호는 숫자 10자리여야 합니다"
        )
        String businessNumber,

        @Schema(description = "개업일자", example = "2024-01-15")
        @NotNull(message = "개업일자는 필수입니다")
        LocalDate openingDate,

        @Schema(description = "대표자명", example = "홍길동")
        @NotBlank(message = "대표자명은 필수입니다")
        String representativeName,

        @Schema(
                description = "사업자등록증 이미지 키",
                example = "business-verifications/1/certificate.jpg"
        )
        @NotBlank(message = "사업자등록증 이미지는 필수입니다")
        String registrationImageKey,

        @Schema(description = "상호명", example = "여기도 카페")
        @NotBlank(message = "상호명은 필수입니다")
        String businessName,

        @Schema(
                description = "사업장 주소",
                example = "서울특별시 강남구 테헤란로 123"
        )
        @NotBlank(message = "사업장 주소는 필수입니다")
        String businessAddress,

        @Valid
        @NotNull(message = "카카오 장소 정보는 필수입니다")
        @Schema(description = "인증할 사업장의 카카오 장소 정보")
        PlaceRequest place

) {
    public BusinessVerifyReqDTO {
        if (businessNumber != null) {
            businessNumber = businessNumber.replaceAll("[\\s-]", "");
        }
    }
}

package com.yeogido.backend.domain.placeenrichment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlaceOverviewApiReleaseReqDTO(
        @Schema(description = "공급자", example = "tour")
        @NotBlank @Pattern(regexp = "[a-z]+") @Size(max = 30) String provider,
        @Schema(description = "차단 해제할 API 작업명", example = "/detailCommon2")
        @NotBlank @Pattern(regexp = "[^:]+") @Size(max = 69) String operation
) {
}

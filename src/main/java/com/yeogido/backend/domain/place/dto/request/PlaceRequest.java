package com.yeogido.backend.domain.place.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "카카오 장소 정보")
public record PlaceRequest(

        @NotBlank
        String externalPlaceId,

        @NotBlank
        String source,

        @NotBlank
        String name,

        String categoryGroupCode,

        String roadAddress,

        String lotAddress,

        @NotNull
        BigDecimal latitude,

        @NotNull
        BigDecimal longitude,

        @NotNull
        Long regionId
) {
}
package com.yeogido.backend.domain.user.dto;

import com.yeogido.backend.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(name = "BusinessVerifyResDTO", description = "소상공인 사업자 인증 응답")

public record BusinessVerifyResDTO(
        @Schema(description = "인증 후 사용자 권한", example = "BUSINESS")
        UserRole role,

        @Schema(description = "사업자 정보ID", example = "10")
        Long businessInfoId
) {}
package com.yeogido.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(
        name = "BusinessInfoResponse",
        description = "인증 사업장 목록 항목"
)
public record BusinessInfoResDTO(

        @Schema(description = "인증 사업장 정보 ID", example = "10")
        Long businessInfoId,

        @Schema(description = "사업자등록번호", example = "1234567890")
        String businessNumber,

        @Schema(description = "상호명", example = "여기도 식당")
        String businessName,

        @Schema(description = "사업장 주소", example = "서울특별시 강남구 테헤란로 1")
        String businessAddress,

        @Schema(description = "대표자명", example = "홍길동")
        String representativeName,

        @Schema(description = "사업자 인증 완료 시각", example = "2026-07-26T15:30:00"
        )
        LocalDateTime verifiedAt

) { }
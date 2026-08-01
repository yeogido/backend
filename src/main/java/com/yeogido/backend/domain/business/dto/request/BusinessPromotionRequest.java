package com.yeogido.backend.domain.business.dto.request;

import com.yeogido.backend.domain.business.enums.PromotionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.LocalTime;
import java.util.List;

public class BusinessPromotionRequest {
    @Builder
    @Schema(name = "BusinessRegisterReqDTO", description = "소상공인 홍보 등록 요청")
    public record Register(

            @NotNull(message = "사업장 인증 정보 ID는 필수입니다")
            @Schema(
                    description = "홍보할 승인된 사업장 인증 정보 ID",
                    example = "1"
            )
            Long businessInfoId,

            @NotBlank(message = "짧은 소개글은 필수입니다")
            @Schema(description = "짧은 소개글", example = "바다 뷰 완전 잘 보이는 카페!")
            String shortDescription,

            @NotBlank(message = "사장님의 한마디는 필수입니다")
            @Schema(description = "사장님의 한마디", example = "부산 바다를 담은 공간, 웨이브온 커피에 오신 걸 환영합니다!")
            String ownerComment,

            @Valid
            @NotNull(message = "영업시간 정보는 필수입니다")
            @Schema(description = "요일별 영업시간")
            @Size(min = 1, message = "영업시간은 최소 1개 이상 입력해야 합니다")
            List<BusinessHour> businessHours,

            @Schema(description = "SNS 계정", example = "https://instagram.com/waveoncoffee")
            String snsAccount,

            @NotBlank(message = "전화번호는 필수입니다")
            @Schema(description = "전화번호", example = "051-727-1660")
            String phoneNumber,

            @Schema(description = "해시태그 ID 목록", example = "[1, 2, 3]")
            List<Long> hashtagIds,

            @NotNull(message = "카테고리는 필수입니다")
            @Schema(description = "카테고리", example = "CAFE")
            PromotionCategory promotionCategory,

            @Valid
            @NotNull(message = "홍보 이미지는 필수입니다")
            @Schema(description = "홍보 이미지")
            @Size(min = 1, max = 5, message = "이미지는 최소 1개, 최대 5개까지 등록할 수 있습니다")
            List<Image> images
    ){ }

    @Builder
    @Schema(name = "BusinessUpdateReqDTO", description = "소상공인 홍보 수정 요청")
    public record Update(

            @Schema(
                    description = "변경할 승인된 사업장 인증 정보 ID",
                    example = "1"
            )
            Long businessInfoId,

            @Schema(
                    description = "짧은 소개글",
                    example = "바다를 바라보며 즐기는 향긋한 커피와 디저트")
            String shortDescription,

            @Schema(
                    description = "사장님의 한마디",
                    example = "부산 바다를 담은 공간, 웨이브온 커피에 오신 걸 환영합니다!")
            String ownerComment,

            @Valid
            @Size(min = 1, message = "영업시간은 최소 1개 이상 입력해야 합니다")
            @Schema(description = "요일별 영업시간")
            List<BusinessHour> businessHours,

            @Schema(
                    description = "SNS 계정",
                    example = "https://instagram.com/waveoncoffee")
            String snsAccount,

            @Schema(description = "전화번호", example = "051-727-1660")
            String phoneNumber,

            @Schema(description = "해시태그 ID 목록", example = "[1, 2, 3]")
            List<Long> hashtagIds,

            @Schema(description = "홍보 카테고리", example = "CAFE")
            PromotionCategory promotionCategory,

            @Valid
            @Size(min = 1, max = 5, message = "이미지는 최소 1개, 최대 5개까지 등록할 수 있습니다")
            @Schema(description = "교체할 홍보 이미지 목록")
            List<Image> images
    ) { }

    @Builder
    @Schema(description = "요일별 영업시간")
    public record BusinessHour(

            @NotBlank(message = "요일은 필수입니다")
            @Schema(description = "요일", example = "MONDAY")
            String dayOfWeek,

            @NotNull(message = "오픈 시간은 필수입니다.")
            @Schema(description = "오픈 시간", example = "10:00")
            LocalTime openTime,

            @NotNull(message = "마감 시간은 필수입니다.")
            @Schema(description = "마감 시간", example = "22:00")
            LocalTime closeTime
    ) { }

    @Builder
    @Schema(description = "홍보 이미지")
    public record Image(

            @NotBlank(message = "이미지 key는 필수입니다")
            @Schema(description = "이미지 key", example = "example = \"temp/8e1f1d4c-1d2f-4f2d-a7b2-9c3d4e5f6a7b.jpg\"")
            String imageKey,

            @NotNull(message = "이미지 정렬 순서는 필수입니다")
            @Min(value = 1, message = "이미지 정렬 순서는 1 이상이어야 합니다")
            @Max(value = 5, message = "이미지 정렬 순서는 5 이하여야 합니다")
            @Schema(description = "이미지 정렬 순서", example = "1")
            Integer sortOrder
    ) { }
}

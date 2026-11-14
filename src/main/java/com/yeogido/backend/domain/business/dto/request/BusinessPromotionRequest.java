package com.yeogido.backend.domain.business.dto.request;

import com.yeogido.backend.domain.business.enums.PromotionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.LocalTime;
import java.util.List;

public class BusinessPromotionRequest {

    /** DB 컬럼 길이(business_promotion.short_description VARCHAR(255)) */
    public static final int SHORT_DESCRIPTION_MAX_LENGTH = 255;

    /** DB 컬럼 길이(business_promotion.phone_number VARCHAR(20)) */
    public static final int PHONE_NUMBER_MAX_LENGTH = 20;

    /** DB 컬럼 길이(business_promotion.sns_account VARCHAR(100)) */
    public static final int SNS_ACCOUNT_MAX_LENGTH = 100;

    /** 숫자, 하이픈, 공백, +만 허용하고 숫자는 8자리 이상 */
    public static final String PHONE_NUMBER_PATTERN =
            "^(?=(?:\\D*\\d){8})[0-9+\\- ]+$";

    /** http:// 또는 https://로 시작하는 공백 없는 주소 */
    public static final String SNS_ACCOUNT_PATTERN = "^https?://\\S+$";

    private static final String SHORT_DESCRIPTION_LENGTH_MESSAGE =
            "짧은 소개글은 최대 255자까지 입력할 수 있습니다";

    private static final String PHONE_NUMBER_LENGTH_MESSAGE =
            "전화번호는 최대 20자까지 입력할 수 있습니다";

    private static final String PHONE_NUMBER_PATTERN_MESSAGE =
            "전화번호는 숫자, 하이픈, 공백, +만 사용할 수 있고 숫자가 8자리 이상이어야 합니다";

    private static final String SNS_ACCOUNT_LENGTH_MESSAGE =
            "SNS 계정은 최대 100자까지 입력할 수 있습니다";

    private static final String SNS_ACCOUNT_PATTERN_MESSAGE =
            "SNS 계정은 http:// 또는 https://로 시작하는 주소여야 합니다";

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
            @Size(max = SHORT_DESCRIPTION_MAX_LENGTH, message = SHORT_DESCRIPTION_LENGTH_MESSAGE)
            @Schema(description = "짧은 소개글 (최대 255자)", example = "바다 뷰 완전 잘 보이는 카페!")
            String shortDescription,

            @Valid
            @NotNull(message = "영업시간 정보는 필수입니다")
            @Schema(description = "요일별 영업시간")
            @Size(min = 1, message = "영업시간은 최소 1개 이상 입력해야 합니다")
            List<BusinessHour> businessHours,

            @Size(max = SNS_ACCOUNT_MAX_LENGTH, message = SNS_ACCOUNT_LENGTH_MESSAGE)
            @Pattern(regexp = SNS_ACCOUNT_PATTERN, message = SNS_ACCOUNT_PATTERN_MESSAGE)
            @Schema(
                    description = "SNS 계정 (최대 100자, http:// 또는 https://로 시작)",
                    example = "https://instagram.com/waveoncoffee"
            )
            String snsAccount,

            @NotBlank(message = "전화번호는 필수입니다")
            @Size(max = PHONE_NUMBER_MAX_LENGTH, message = PHONE_NUMBER_LENGTH_MESSAGE)
            @Pattern(regexp = PHONE_NUMBER_PATTERN, message = PHONE_NUMBER_PATTERN_MESSAGE)
            @Schema(
                    description = "전화번호 (최대 20자, 숫자·하이픈·공백·+만 허용, 숫자 8자리 이상)",
                    example = "051-727-1660"
            )
            String phoneNumber,

            @Schema(description = "해시태그 ID 목록", example = "[1, 2, 3]")
            @Size(max = 5, message = "해시태그는 최대 5개까지 선택할 수 있습니다.")
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

            @Size(max = SHORT_DESCRIPTION_MAX_LENGTH, message = SHORT_DESCRIPTION_LENGTH_MESSAGE)
            @Schema(
                    description = "짧은 소개글 (최대 255자)",
                    example = "바다를 바라보며 즐기는 향긋한 커피와 디저트")
            String shortDescription,

            @Valid
            @Size(min = 1, message = "영업시간은 최소 1개 이상 입력해야 합니다")
            @Schema(description = "요일별 영업시간")
            List<BusinessHour> businessHours,

            @Size(max = SNS_ACCOUNT_MAX_LENGTH, message = SNS_ACCOUNT_LENGTH_MESSAGE)
            @Pattern(regexp = SNS_ACCOUNT_PATTERN, message = SNS_ACCOUNT_PATTERN_MESSAGE)
            @Schema(
                    description = "SNS 계정 (최대 100자, http:// 또는 https://로 시작)",
                    example = "https://instagram.com/waveoncoffee")
            String snsAccount,

            @Schema(
                    description = "기존 SNS 계정 삭제 여부",
                    example = "false"
            )
            Boolean clearSnsAccount,

            @Size(max = PHONE_NUMBER_MAX_LENGTH, message = PHONE_NUMBER_LENGTH_MESSAGE)
            @Pattern(regexp = PHONE_NUMBER_PATTERN, message = PHONE_NUMBER_PATTERN_MESSAGE)
            @Schema(
                    description = "전화번호 (최대 20자, 숫자·하이픈·공백·+만 허용, 숫자 8자리 이상)",
                    example = "051-727-1660"
            )
            String phoneNumber,

            @Schema(description = "해시태그 ID 목록", example = "[1, 2, 3]")
            @Size(max = 5, message = "해시태그는 최대 5개까지 선택할 수 있습니다.")
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
            @Schema(description = "이미지 key", example = "temp/8e1f1d4c-1d2f-4f2d-a7b2-9c3d4e5f6a7b.jpg")
            String imageKey,

            @NotNull(message = "이미지 정렬 순서는 필수입니다")
            @Min(value = 1, message = "이미지 정렬 순서는 1 이상이어야 합니다")
            @Max(value = 5, message = "이미지 정렬 순서는 5 이하여야 합니다")
            @Schema(description = "이미지 정렬 순서", example = "1")
            Integer sortOrder
    ) { }
}

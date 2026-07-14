package com.yeogido.backend.domain.business.dto.request;

import com.yeogido.backend.domain.business.enums.PromotionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public class BusinessPromotionRequest {
    @Builder
    @Schema(name = "BusinessRegisterReqDTO", description = "소상공인 홍보 등록 요청")
    public record Register(

            @Valid
            @NotNull(message = "장소 정보는 필수입니다")
            @Schema(description = "홍보 장소 정보")
            Place place,

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
    @Schema(name = "소상공인 홍보 수정 요청", description = "BusinessUpdateReqDTO")
    public record Update(

            @Valid
            @Schema(description = "변경할 장소 정보. 카카오맵에서 다른 장소를 선택한 경우에만 전달합니다")
            Place place,

            @Schema(
                    description = "짧은 소개글",
                    example = "바다를 바라보며 즐기는 향긋한 커피와 디저트")
            String shortDescription,

            @Schema(
                    description = "사장님의 한마디",
                    example = "부산 바다를 담은 공간, 웨이브온 커피에 오신 걸 환영합니다!")
            String ownerComment,

            @Valid
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
            @Schema(description = "교체할 홍보 이미지 목록")
            List<Image> images
    ) { }

    @Builder
    @Schema(description = "장소 정보")
    public record Place(

            @Schema(description = "외부 장소ID", example = "1234567890")
            String externalPlaceId,

            @NotBlank(message = "장소 정보 출처는 필수입니다")
            @Schema(description = "장소 정보 출처", example = "KAKAO")
            String source,

            @NotBlank(message = "장소명은 필수입니다")
            @Schema(description = "장소명", example = "웨이브온 커피")
            String name,

            @Schema(description = "카테고리 그룹 코드", example = "CE7")
            String categoryGroupCode,

            @Schema(description = "도로명 주소", example = "부산 기장군 장안읍 해맞이로 286")
            String roadAddress,

            @Schema(description = "지번 주소", example = "부산 기장군 장안읍 월내리 553")
            String lotAddress,

            @NotNull(message = "위도는 필수입니다")
            @Schema(description = "위도", example = "35.3214567")
            BigDecimal latitude,

            @NotNull(message = "경도는 필수입니다")
            @Schema(description = "경도", example = "129.2741234")
            BigDecimal longitude,

            @NotNull(message = "지역ID는 필수입니다")
            @Schema(description = "지역ID", example = "26")
            Long regionId
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
            @Schema(description = "이미지 key", example = "business-promotions/1/image1.jpg")
            String imageKey,

            @NotNull(message = "이미지 정렬 순서는 필수입니다")
            @Schema(description = "이미지 정렬 순서", example = "1")
            Integer sortOrder
    ) { }
}

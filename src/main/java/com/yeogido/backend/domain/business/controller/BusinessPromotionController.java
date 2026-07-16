package com.yeogido.backend.domain.business.controller;

import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Tag(name = "Business Promotion", description = "소상공인 홍보 API")
@RestController
@RequestMapping("/api/v1/business-promotions")
public class BusinessPromotionController {

    @Operation(summary = "소상공인 홍보 등록", description = "소상공인 홍보글을 등록합니다")
    @PostMapping
    public ApiResponse<BusinessPromotionResponse.Register> registerBusinessPromotion(
            @Valid @RequestBody BusinessPromotionRequest.Register request
    ) {
        BusinessPromotionResponse.Register response = BusinessPromotionResponse.Register.builder()
                .promotionId(1L)
                .build();

        return ApiResponse.onSuccess(SuccessCode.CREATED, response);
    }

    @Operation(
            summary = "소상공인 홍보 수정",
            description = "현재 사용자가 등록한 소상공인 홍보글을 수정합니다")

    @PatchMapping("/{promotionId}")
    public ApiResponse<BusinessPromotionResponse.Update> updateBusinessPromotion(
            @Parameter(
                    description = "수정할 소상공인 홍보 ID",
                    required = true,
                    example = "1")
            @PathVariable Long promotionId,
            @Valid @RequestBody BusinessPromotionRequest.Update request
    ) {
        BusinessPromotionResponse.Update response = BusinessPromotionResponse.Update.builder()
                .promotionId(promotionId)
                .build();
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "소상공인 홍보 삭제",
            description = "현재 사용자가 등록한 소상공인 홍보글을 삭제합니다")

    @DeleteMapping("/{promotionId}")
    public ApiResponse<Void> deleteBusinessPromotion(
            @Parameter(
                    description = "삭제할 소상공인 홍보 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long promotionId
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    @Operation(summary = "소상공인 홍보 목록 조회", description = "소상공인 홍보 목록을 조회합니다")
    @GetMapping
    public ApiResponse<CursorResponse<BusinessPromotionResponse.Summary>> getBusinessPromotions(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) PromotionCategory category,
            @RequestParam(required = false) Long regionId,
            @RequestParam(defaultValue = "LATEST") String sort
    ) {
        BusinessPromotionResponse.Summary summary = BusinessPromotionResponse.Summary.builder()
                .promotionId(1L)
                .placeId(10L)
                .placeName("웨이브온 커피")
                .promotionCategory(PromotionCategory.CAFE)
                .roadAddress("부산 기장군 장안읍 해맞이로 286")
                .regionId(26L)
                .regionName("부산광역시")
                .thumbnailImageUrl("https://example.com/image.jpg")
                .shortDescription("바다 뷰 완전 잘 보이는 카페!")
                .likeCount(24)
                .isLiked(true)
                .createdAt(LocalDateTime.now())
                .build();

        CursorResponse<BusinessPromotionResponse.Summary> response =
                CursorResponse.of(List.of(summary), 1L,null, false);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "내가 등록한 홍보글 조회", description = "현재 사용자가 등록한 소상공인 홍보글 목록을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<CursorResponse<BusinessPromotionResponse.MySummary>> getMyBusinessPromotions(            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        BusinessPromotionResponse.MySummary summary = BusinessPromotionResponse.MySummary.builder()
                .promotionId(1L)
                .placeId(10L)
                .placeName("웨이브온 커피")
                .promotionCategory(PromotionCategory.CAFE)
                .roadAddress("부산 기장군 장안읍 해맞이로 286")
                .thumbnailImageUrl("https://example.com/image.jpg")
                .shortDescription("바다 뷰 완전 잘 보이는 카페!")
                .status("ACTIVE")
                .likeCount(24)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CursorResponse<BusinessPromotionResponse.MySummary> response =
                CursorResponse.of(List.of(summary), 1L,null, false);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "소상공인 홍보 상세 조회", description = "소상공인 홍보글 상세 정보를 조회합니다.")
    @GetMapping("/{promotionId}")
    public ApiResponse<BusinessPromotionResponse.Detail> getBusinessPromotion(
            @PathVariable Long promotionId
    ) {
        BusinessPromotionResponse.PlaceInfo place = BusinessPromotionResponse.PlaceInfo.builder()
                .placeId(10L)
                .name("웨이브온 커피")
                .categoryGroupCode("CE7")
                .roadAddress("부산 기장군 장안읍 해맞이로 286")
                .lotAddress("부산 기장군 장안읍 월내리 553")
                .latitude(new BigDecimal("35.3214567"))
                .longitude(new BigDecimal("129.2741234"))
                .regionId(26L)
                .regionName("부산광역시")
                .build();

        BusinessPromotionResponse.BusinessHourInfo businessHour =
                BusinessPromotionResponse.BusinessHourInfo.builder()
                        .dayOfWeek("MONDAY")
                        .openTime(LocalTime.of(10, 0))
                        .closeTime(LocalTime.of(22, 0))
                        .build();

        BusinessPromotionResponse.ImageInfo image = BusinessPromotionResponse.ImageInfo.builder()
                .imageUrl("https://example.com/image.jpg")
                .sortOrder(1)
                .build();

        BusinessPromotionResponse.Detail response = BusinessPromotionResponse.Detail.builder()
                .promotionId(promotionId)
                .place(place)
                .promotionCategory(PromotionCategory.CAFE)
                .shortDescription("바다 뷰 완전 잘 보이는 카페!")
                .ownerComment("부산 바다를 담은 공간, 웨이브온 커피에 오신 걸 환영합니다!")
                .businessHours(List.of(businessHour))
                .snsAccount("https://instagram.com/waveoncoffee")
                .phoneNumber("051-727-1660")
                .hashtags(List.of("오션뷰", "부산카페", "디저트"))
                .images(List.of(image))
                .likeCount(24)
                .isLiked(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

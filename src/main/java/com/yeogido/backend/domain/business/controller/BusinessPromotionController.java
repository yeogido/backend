package com.yeogido.backend.domain.business.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionSortType;
import com.yeogido.backend.domain.business.service.BusinessPromotionService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Business Promotion", description = "소상공인 홍보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/business-promotions")
@Validated
public class BusinessPromotionController {

    private final BusinessPromotionService businessPromotionService;

    @Operation(summary = "소상공인 홍보 등록", description = "소상공인 홍보글을 등록합니다")
    @PostMapping
    public ApiResponse<BusinessPromotionResponse.Register> registerBusinessPromotion(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody BusinessPromotionRequest.Register request
    ) {
        BusinessPromotionResponse.Register response =
                businessPromotionService.registerBusinessPromotion(
                        authUser.userId(),
                        request
                );

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
    public ApiResponse<CursorResponse<BusinessPromotionResponse.Summary>>
    getBusinessPromotions(
            @AuthenticationPrincipal AuthUser authUser,

            @RequestParam(required = false)
            String cursorValue,

            @RequestParam(required = false)
            Long cursorId,

            @Positive(message = "조회 개수는 1 이상이어야 합니다")
            @RequestParam(defaultValue = "10")
            Integer size,

            @RequestParam(required = false)
            PromotionCategory category,

            @RequestParam(defaultValue = "RECOMMEND")
            PromotionSortType sort
    ) {
        Long userId = authUser == null
                        ? null
                        : authUser.userId();

        CursorResponse<BusinessPromotionResponse.Summary> response =
                businessPromotionService.getBusinessPromotions(
                        userId,
                        cursorValue,
                        cursorId,
                        size,
                        category,
                        sort
                );

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "내가 등록한 홍보글 조회", description = "현재 사용자가 등록한 소상공인 홍보글 목록을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<CursorResponse<BusinessPromotionResponse.MySummary>>
    getMyBusinessPromotions(
            @AuthenticationPrincipal AuthUser authUser,

            @RequestParam(required = false)
            LocalDateTime cursorValue,

            @RequestParam(required = false)
            Long cursorId,

            @Positive(message = "조회 개수는 1 이상이어야 합니다")
            @RequestParam(defaultValue = "10")
            Integer size
    ) {
        CursorResponse<BusinessPromotionResponse.MySummary> response =
                businessPromotionService.getMyBusinessPromotions(
                        authUser.userId(),
                        cursorValue,
                        cursorId,
                        size
                );

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "소상공인 홍보 상세 조회", description = "소상공인 홍보글 상세 정보를 조회합니다.")
    @GetMapping("/{promotionId}")
    public ApiResponse<BusinessPromotionResponse.Detail> getBusinessPromotion(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long promotionId
    ) {
        Long userId = authUser == null
                        ? null
                        : authUser.userId();

        BusinessPromotionResponse.Detail response =
                businessPromotionService.getBusinessPromotion(
                        userId,
                        promotionId
                );

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

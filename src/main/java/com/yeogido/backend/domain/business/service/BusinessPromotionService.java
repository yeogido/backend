package com.yeogido.backend.domain.business.service;

import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionSortType;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.time.LocalDateTime;

public interface BusinessPromotionService {

    // 홍보글 등록
    BusinessPromotionResponse.Register registerBusinessPromotion(
            Long userId,
            BusinessPromotionRequest.Register request
    );

    // 홍보글 수정
    BusinessPromotionResponse.Update updateBusinessPromotion(
            Long userId,
            Long promotionId,
            BusinessPromotionRequest.Update request
    );

    // 홍보글 삭제
    void deleteBusinessPromotion(
            Long userId,
            Long promotionId
    );

    // 홍보글 상세 조회
    BusinessPromotionResponse.Detail getBusinessPromotion(
            Long userId,
            Long promotionId
    );

    // 내가 등록한 홍보글 조회
    CursorResponse<BusinessPromotionResponse.MySummary>
    getMyBusinessPromotions(
            Long userId,
            LocalDateTime cursorValue,
            Long cursorId,
            Integer size
    );

    // 홍보 목록 조회
    CursorResponse<BusinessPromotionResponse.Summary>
    getBusinessPromotions(
            Long userId,
            String cursorValue,
            Long cursorId,
            Integer size,
            Long regionId,
            PromotionCategory category,
            PromotionSortType sort
    );
}

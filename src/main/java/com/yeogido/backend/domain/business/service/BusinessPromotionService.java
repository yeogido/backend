package com.yeogido.backend.domain.business.service;

import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionSortType;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.time.LocalDateTime;

public interface BusinessPromotionService {

    // 홍보글 등록 interface
    BusinessPromotionResponse.Register registerBusinessPromotion(
            BusinessPromotionRequest.Register request
    );

    // 홍보글 상세 조회 interface
    BusinessPromotionResponse.Detail getBusinessPromotion(Long promotionId);

    // 내가 등록한 홍보글 조회 interface
    CursorResponse<BusinessPromotionResponse.MySummary> getMyBusinessPromotions(
            LocalDateTime cursorValue, Long cursor, Integer size);

    // 홍보 목록 조회 interface
    CursorResponse<BusinessPromotionResponse.Summary> getBusinessPromotions(
            String cursorValue,
            Long cursorId,
            Integer size,
            PromotionCategory category,
            PromotionSortType sort
    );
}

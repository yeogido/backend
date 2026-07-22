package com.yeogido.backend.domain.business.service;

import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;

public interface BusinessPromotionService {

    // 홍보글 등록 interface
    BusinessPromotionResponse.Register registerBusinessPromotion(
            BusinessPromotionRequest.Register request
    );
}

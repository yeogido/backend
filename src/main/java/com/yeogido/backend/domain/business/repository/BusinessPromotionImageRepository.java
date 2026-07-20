package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessPromotionImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPromotionImageRepository
        extends JpaRepository<BusinessPromotionImage, Long> {

    void deleteAllByPromotion_Id(Long promotionId);
}

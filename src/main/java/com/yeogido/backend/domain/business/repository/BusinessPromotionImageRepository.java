package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessPromotionImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessPromotionImageRepository
        extends JpaRepository<BusinessPromotionImage, Long> {

    void deleteAllByPromotion_Id(Long promotionId);

    List<BusinessPromotionImage> findAllByPromotion_IdOrderBySortOrderAsc(Long promotionId);

    List<BusinessPromotionImage> findAllByPromotion_IdInAndSortOrder(
            List<Long> promotionIds,
            Integer sortOrder
    );
}

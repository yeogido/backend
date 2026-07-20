package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessPromotionHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPromotionHashtagRepository
        extends JpaRepository<BusinessPromotionHashtag, Long> {

    void deleteAllByPromotion_Id(Long promotionId);
}

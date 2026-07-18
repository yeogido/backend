package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessPromotionRepository
        extends JpaRepository<BusinessPromotion, Long> {

    Optional<BusinessPromotion> findByPlaceId(Long placeId);
}

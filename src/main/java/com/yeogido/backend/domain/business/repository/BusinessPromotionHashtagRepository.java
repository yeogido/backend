package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessPromotionHashtag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessPromotionHashtagRepository
        extends JpaRepository<BusinessPromotionHashtag, Long> {

    void deleteAllByPromotion_Id(Long promotionId);

    @EntityGraph(attributePaths = "hashtag")
    List<BusinessPromotionHashtag> findAllByPromotion_IdOrderByHashtag_IdAsc(Long promotionId);

    @EntityGraph(attributePaths = "hashtag")
    List<BusinessPromotionHashtag> findAllByPromotion_IdInOrderByHashtag_IdAsc(
            List<Long> promotionIds
    );
}
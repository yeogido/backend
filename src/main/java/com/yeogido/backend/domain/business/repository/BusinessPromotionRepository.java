package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BusinessPromotionRepository
        extends JpaRepository<BusinessPromotion, Long> {

    Optional<BusinessPromotion> findByPlaceId(Long placeId);

    @Modifying(flushAutomatically = true)
    @Query("""
        update BusinessPromotion promotion
        set promotion.status = com.yeogido.backend.domain.business.enums.PromotionStatus.DELETED
        where promotion.user.id = :userId
          and promotion.status = com.yeogido.backend.domain.business.enums.PromotionStatus.ACTIVE
        """)
    int softDeleteAllActiveByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"place", "place.region"})
    Optional<BusinessPromotion> findByIdAndStatus(Long id, PromotionStatus status);

    @EntityGraph(attributePaths = "place")
    List<BusinessPromotion> findByUserIdAndStatusOrderByCreatedAtDescIdDesc(
            Long userId,
            PromotionStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "place")
    @Query("""
        SELECT promotion
        FROM BusinessPromotion promotion
        WHERE promotion.user.id = :userId
          AND promotion.status = :status
          AND (
                promotion.createdAt < :cursorValue
                OR (
                    promotion.createdAt = :cursorValue
                    AND promotion.id < :cursorId
                )
          )
        ORDER BY promotion.createdAt DESC, promotion.id DESC
        """)
    List<BusinessPromotion> findMyPromotionsAfterCursor(
            @Param("userId") Long userId,
            @Param("status") PromotionStatus status,
            @Param("cursorValue") LocalDateTime cursorValue,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}

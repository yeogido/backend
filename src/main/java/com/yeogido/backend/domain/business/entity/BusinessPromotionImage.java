package com.yeogido.backend.domain.business.entity;

import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "business_promotion_image",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_business_promotion_image_promotion_sort_order",
                        columnNames = {"promotion_id", "sort_order"}
                )
        }
)
public class BusinessPromotionImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private BusinessPromotion promotion;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
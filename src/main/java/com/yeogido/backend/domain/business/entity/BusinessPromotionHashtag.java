package com.yeogido.backend.domain.business.entity;

import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "business_promotion_hashtag",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_business_promotion_hashtag",
                        columnNames = {"promotion_id", "hashtag_id"}
                )
        }
)
public class BusinessPromotionHashtag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private BusinessPromotion promotion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;
}
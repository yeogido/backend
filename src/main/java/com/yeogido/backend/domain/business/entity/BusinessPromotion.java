package com.yeogido.backend.domain.business.entity;

import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "business_promotion")
public class BusinessPromotion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false, unique = true)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "short_description", length = 255)
    private String shortDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_category", nullable = false, length = 20)
    private PromotionCategory promotionCategory;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionStatus status;

    @Column(name = "sns_account", length = 100)
    private String snsAccount;

    @Builder.Default
    @Column(name = "recommendation_priority", nullable = false)
    private Integer recommendationPriority = 0;

    public void clearSnsAccount() {
        this.snsAccount = null;
    }

    public void update(
            String shortDescription,
            PromotionCategory promotionCategory,
            String phoneNumber,
            String snsAccount
    ) {
        if (shortDescription != null) {
            this.shortDescription = shortDescription;
        }

        if (promotionCategory != null) {
            this.promotionCategory = promotionCategory;
        }

        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }

        if (snsAccount != null) {
            this.snsAccount = snsAccount;
        }
    }

    public void delete() {
        this.status = PromotionStatus.DELETED;
    }

    /**
     * 삭제된 홍보글을 다시 활성화한다. 작성자가 바뀐 경우(가게 양도 등)에는
     * 새 작성자로 교체되며, 영업시간, 사진, 해시태그는 호출 전에 비워져 있어야 한다.
     */
    public void reactivate(
            User owner,
            String shortDescription,
            PromotionCategory promotionCategory,
            String phoneNumber,
            String snsAccount
    ) {
        this.user = owner;
        this.shortDescription = shortDescription;
        this.promotionCategory = promotionCategory;
        this.phoneNumber = phoneNumber;
        this.snsAccount = snsAccount;
        this.status = PromotionStatus.ACTIVE;
        this.recommendationPriority = 0;
    }
}
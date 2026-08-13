package com.yeogido.backend.domain.business.entity;

import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "business_operating_day",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_business_operating_day_promotion_day",
                        columnNames = {"promotion_id", "day_of_week"}
                )
        }
)
public class BusinessOperatingDay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private BusinessPromotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;
}

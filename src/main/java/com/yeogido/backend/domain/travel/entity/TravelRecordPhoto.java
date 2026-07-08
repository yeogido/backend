package com.yeogido.backend.domain.travel.entity;

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
        name = "travel_record_photo",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_record_photo_order",
                        columnNames = {"travel_record_id", "image_order"}
                )
        }
)
public class TravelRecordPhoto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_record_id", nullable = false)
    private TravelRecord travelRecord;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "image_order", nullable = false)
    private Integer imageOrder;
}
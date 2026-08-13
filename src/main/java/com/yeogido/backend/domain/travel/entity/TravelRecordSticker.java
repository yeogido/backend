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
        name = "travel_record_sticker",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_travel_record_sticker_record_z_index",
                        columnNames = {"travel_record_id", "z_index"}
                )
        }
)
public class TravelRecordSticker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_record_id", nullable = false)
    private TravelRecord travelRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sticker_id", nullable = false)
    private Sticker sticker;

    @Column(name = "position_x", nullable = false)
    private Double positionX;

    @Column(name = "position_y", nullable = false)
    private Double positionY;

    @Column(nullable = false)
    private Double rotation;

    @Column(nullable = false)
    private Double scale;

    @Column(name = "z_index", nullable = false)
    private Integer zIndex;
}

package com.yeogido.backend.domain.place.entity;

import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "place",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_place_source_external_id",
                        columnNames = {"source", "external_place_id"}
                )
        }
)
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(nullable = false, name = "external_place_id", length = 100)
    private String externalPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceSource source;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "category_group_code", length = 20)
    private String categoryGroupCode;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "lot_address", length = 255)
    private String lotAddress;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    public void updateTourData(
            Region region,
            String name,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.region = region;
        this.name = name;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.latitude = latitude;
        this.longitude = longitude;
    }

}

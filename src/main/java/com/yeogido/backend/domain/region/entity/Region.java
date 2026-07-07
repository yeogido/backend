package com.yeogido.backend.domain.region.entity;

import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "region")
public class Region extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 상위 지역
     * ex) 부산광역시 -> 해운대구
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "full_name", length = 50)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegionType type;

    @Column(name = "image_key", length = 255)
    private String imageKey;
}
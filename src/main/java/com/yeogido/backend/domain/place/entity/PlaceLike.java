package com.yeogido.backend.domain.place.entity;

import com.yeogido.backend.domain.place.enums.PlaceLikeSourceType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "place_like",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_place_like_user_place",
                        columnNames = {"user_id", "place_id"}
                )
        }
)
public class PlaceLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private PlaceLikeSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;
}

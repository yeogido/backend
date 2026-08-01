package com.yeogido.backend.domain.user.entity;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "business_info",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_business_info_business_number",
                        columnNames = "business_number"
                )
        }
)
public class BusinessInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "place_id",
            unique = true,
            foreignKey = @ForeignKey(
                    name = "fk_business_info_place"
            )
    )
    private Place place;

    @Column(
            name = "business_number",
            nullable = false,
            length = 10
    )
    private String businessNumber;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(
            name = "representative_name",
            nullable = false,
            length = 100
    )
    private String representativeName;

    @Column(
            name = "registration_image_key",
            nullable = false,
            length = 255
    )
    private String registrationImageKey;

    @Column(
            name = "business_name",
            nullable = false,
            length = 100
    )
    private String businessName;

    @Column(
            name = "business_address",
            nullable = false,
            length = 255
    )
    private String businessAddress;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "verification_status",
            nullable = false,
            length = 20
    )
    private BusinessVerificationStatus verificationStatus;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    public void reverifyAndConnectPlace(
            Place place,
            LocalDate openingDate,
            String representativeName,
            String registrationImageKey,
            String businessName,
            String businessAddress,
            LocalDateTime verifiedAt
    ) {
        this.place = place;
        this.openingDate = openingDate;
        this.representativeName = representativeName;
        this.registrationImageKey = registrationImageKey;
        this.businessName = businessName;
        this.businessAddress = businessAddress;
        this.verificationStatus =
                BusinessVerificationStatus.APPROVED;
        this.verifiedAt = verifiedAt;
    }
}
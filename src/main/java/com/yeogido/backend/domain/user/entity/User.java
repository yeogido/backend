package com.yeogido.backend.domain.user.entity;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.enums.AgeGroup;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_email",
                        columnNames = "email"
                )
        }
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = true)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_year", nullable = false, length = 4)
    private String birthYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    public void promoteToBusiness() {
        if (this.role == UserRole.USER) {
            this.role = UserRole.BUSINESS;
        }
    }

    public AgeGroup getAgeGroup() {
        return AgeGroup.fromBirthYear(birthYear);
    }

    public void withdraw() {
        this.status = UserStatus.DELETED;
    }

    public void updateProfile(
            String nickname,
            String birthYear,
            Region region
    ) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (birthYear != null) {
            this.birthYear = birthYear;
        }
        if (region != null) {
            this.region = region;
        }
    }
}

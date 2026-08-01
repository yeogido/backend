package com.yeogido.backend.domain.travel.entity;

import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
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
        name = "sticker",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sticker_image_key",
                        columnNames = "image_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_sticker_type_category",
                        columnList = "sticker_type, category"
                ),
                @Index(
                        name = "idx_sticker_user_type_deleted",
                        columnList = "user_id, sticker_type, deleted_at"
                )
        }
)
public class Sticker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "image_key", nullable = false, unique = true, length = 255)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "sticker_type", nullable = false, length = 20)
    private StickerType stickerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StickerCategory category;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

package com.yeogido.backend.domain.content.entity;

import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.content.enums.ContentStatus;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "content")
public class Content extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "external_content_id", length = 100)
    private String externalContentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentSource source;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_image", length = 500)
    private String thumbnailImage;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "external_details_synced_at")
    private java.time.LocalDateTime externalDetailsSyncedAt;

    @Enumerated(EnumType.STRING)
    private ContentCategory category;

    @Column(name = "recommend_priority", nullable = false)
    @Builder.Default
    private Integer recommendPriority = 0;

    public ContentStatus getEventStatus() {
        LocalDate today = LocalDate.now();

        if (startDate != null && today.isBefore(startDate)) {
            return ContentStatus.BEFORE;
        }

        if (endDate != null && today.isAfter(endDate)) {
            return ContentStatus.AFTER;
        }

        return ContentStatus.ONGOING;
    }

    public void update(
            Place place,
            String externalContentId,
            String title,
            String description,
            String thumbnailImage,
            LocalDate startDate,
            LocalDate endDate,
            String contactPhone,
            ContentCategory category,
            ContentSource source
    ) {
        if (place != null) {
            this.place = place;
        }
        if (externalContentId != null) {
            this.externalContentId = externalContentId;
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (thumbnailImage != null) {
            this.thumbnailImage = thumbnailImage;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
        if (contactPhone != null) {
            this.contactPhone = contactPhone;
        }
        if (category != null) {
            this.category = category;
        }
        if (source != null) {
            this.source = source;
        }
    }

    public void updateRecommendPriority(Integer recommendPriority) {
        this.recommendPriority = recommendPriority;
    }

    public void updateExternalDetails(
            String description,
            java.time.LocalDateTime synchronizedAt
    ) {
        this.description = description;
        this.externalDetailsSyncedAt = synchronizedAt;
    }
}

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

    @Column(length = 500)
    private String description;

    @Column(name = "thumbnail_image", length = 500)
    private String thumbnailImage;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "official_url", length = 500)
    private String officialUrl;

    @Enumerated(EnumType.STRING)
    private ContentCategory category;

    @Column(name = "recommend_priority", nullable = false)
    @Builder.Default
    private Integer recommendPriority = 9999;

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
            String officialUrl,
            ContentCategory category,
            ContentSource source
    ) {
        this.place = place;
        this.externalContentId = externalContentId;
        this.title = title;
        this.description = description;
        this.thumbnailImage = thumbnailImage;
        this.startDate = startDate;
        this.endDate = endDate;
        this.contactPhone = contactPhone;
        this.officialUrl = officialUrl;
        this.category = category;
        this.source = source;
    }

    public void updateRecommendPriority(Integer recommendPriority) {
        this.recommendPriority = recommendPriority;
    }
}

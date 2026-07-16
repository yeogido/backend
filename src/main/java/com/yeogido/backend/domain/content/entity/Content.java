package com.yeogido.backend.domain.content.entity;

import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSource;
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
}
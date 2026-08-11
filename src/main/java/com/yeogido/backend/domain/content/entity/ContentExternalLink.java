package com.yeogido.backend.domain.content.entity;

import com.yeogido.backend.domain.content.enums.ContentLinkSource;
import com.yeogido.backend.domain.content.enums.ContentLinkType;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "content_external_link")
public class ContentExternalLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 30)
    private ContentLinkType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentLinkSource source;

    @Column(length = 100)
    private String label;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}

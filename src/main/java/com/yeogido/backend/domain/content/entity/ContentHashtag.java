package com.yeogido.backend.domain.content.entity;

import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "content_hashtag",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_content_hashtag",
                        columnNames = {"content_id", "hashtag_id"}
                )
        }
)
public class ContentHashtag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;
}
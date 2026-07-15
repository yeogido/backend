package com.yeogido.backend.domain.course.entity;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "course")
public class Course extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false, length = 20)
    private CourseType courseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_type", length = 20)
    private DurationType durationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", length = 20)
    private TransportType transportType;

    @Column(name = "month_start")
    private Integer monthStart;

    @Column(name = "month_end")
    private Integer monthEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "companion_type", length = 20)
    private CompanionType companionType;

    @Column(name = "thumbnail_key", length = 255)
    private String thumbnailKey;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "recommend_order")
    private Integer recommendOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void update(
            String title,
            String description,
            DurationType durationType,
            TransportType transportType,
            CompanionType companionType,
            Integer monthStart,
            Integer monthEnd,
            String thumbnailKey
    ) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (durationType != null) {
            this.durationType = durationType;
        }
        if (transportType != null) {
            this.transportType = transportType;
        }
        if (companionType != null) {
            this.companionType = companionType;
        }
        if (monthStart != null) {
            this.monthStart = monthStart;
        }
        if (monthEnd != null) {
            this.monthEnd = monthEnd;
        }
        if (thumbnailKey != null) {
            this.thumbnailKey = thumbnailKey;
        }
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}

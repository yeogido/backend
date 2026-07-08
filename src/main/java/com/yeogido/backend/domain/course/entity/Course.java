package com.yeogido.backend.domain.course.entity;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.region.entity.Region;
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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
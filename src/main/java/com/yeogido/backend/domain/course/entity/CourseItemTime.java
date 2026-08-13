package com.yeogido.backend.domain.course.entity;

import com.yeogido.backend.domain.course.enums.TransportMode;
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
import jakarta.persistence.UniqueConstraint;
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
        name = "course_item_time",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_course_item_time_items_mode",
                        columnNames = {"from_course_item_id", "to_course_item_id", "transport_mode"}
                )
        }
)
public class CourseItemTime extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_course_item_id", nullable = false)
    private CourseItem fromCourseItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_course_item_id", nullable = false)
    private CourseItem toCourseItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 20)
    private TransportMode transportMode;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;
}

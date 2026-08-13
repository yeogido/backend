package com.yeogido.backend.domain.course.entity;

import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "course_review_image",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_course_review_image_review_order",
                        columnNames = {"course_review_id", "image_order"}
                )
        }
)
public class CourseReviewImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_review_id", nullable = false)
    private CourseReview courseReview;

    @Column(name = "image_key", nullable = false, length = 255)
    private String imageKey;

    @Column(name = "image_order", nullable = false)
    private Integer imageOrder;
}

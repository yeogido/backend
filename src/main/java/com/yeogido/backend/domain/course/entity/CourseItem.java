package com.yeogido.backend.domain.course.entity;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
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
        name = "course_item",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_course_item_order",
                        columnNames = {"course_id", "order_no"}
                )
        }
)
public class CourseItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private CourseItemType itemType;

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    @Column(name = "image_key", length = 255)
    private String imageKey;

    public void updateOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
}


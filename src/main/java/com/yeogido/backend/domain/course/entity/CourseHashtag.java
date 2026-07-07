package com.yeogido.backend.domain.course.entity;

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
        name = "course_hashtag",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_course_hashtag",
                        columnNames = {"course_id", "hashtag_id"}
                )
        }
)
public class CourseHashtag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;
}
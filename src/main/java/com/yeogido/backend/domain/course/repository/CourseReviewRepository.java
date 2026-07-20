package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseReview;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    @Query("""
            select cr
            from CourseReview cr
            join fetch cr.user
            join cr.course c
            where c.deletedAt is null
            order by cr.createdAt desc, cr.id desc
            """)
    List<CourseReview> findRecentReviews(Pageable pageable);
}

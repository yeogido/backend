package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseReview;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    @Query("""
            select cr
            from CourseReview cr
            join fetch cr.user
            join fetch cr.course c
            where c.deletedAt is null
            order by cr.createdAt desc, cr.id desc
            """)
    List<CourseReview> findRecentReviews(Pageable pageable);

    @Query("""
            select cr
            from CourseReview cr
            join fetch cr.user
            join fetch cr.course c
            where c.deletedAt is null
            order by cr.createdAt desc, cr.id desc
            """)
    List<CourseReview> findReviewsOrderByLatest(Pageable pageable);

    @Query("""
            select cr
            from CourseReview cr
            join fetch cr.user
            join fetch cr.course c
            where c.deletedAt is null
              and (
                    cr.createdAt < :cursorCreatedAt
                    or (cr.createdAt = :cursorCreatedAt and cr.id < :cursorId)
              )
            order by cr.createdAt desc, cr.id desc
            """)
    List<CourseReview> findReviewsOrderByLatestAfterCursor(
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select cr
            from CourseReview cr
            join fetch cr.user
            join fetch cr.course c
            where c.deletedAt is null
            order by cr.rating desc, cr.id desc
            """)
    List<CourseReview> findReviewsOrderByRating(Pageable pageable);

    @Query("""
            select cr
            from CourseReview cr
            join fetch cr.user
            join fetch cr.course c
            where c.deletedAt is null
              and (
                    cr.rating < :cursorRating
                    or (cr.rating = :cursorRating and cr.id < :cursorId)
              )
            order by cr.rating desc, cr.id desc
            """)
    List<CourseReview> findReviewsOrderByRatingAfterCursor(
            @Param("cursorRating") BigDecimal cursorRating,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}

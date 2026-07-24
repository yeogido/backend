package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseReviewImageRepository extends JpaRepository<CourseReviewImage, Long> {

    List<CourseReviewImage> findAllByCourseReview_IdOrderByImageOrderAsc(Long courseReviewId);

    List<CourseReviewImage> findAllByCourseReview_IdInOrderByCourseReview_IdAscImageOrderAsc(
            List<Long> courseReviewIds
    );

    @Modifying
    @Query("delete from CourseReviewImage image where image.courseReview.id = :courseReviewId")
    void deleteAllByCourseReview_Id(@Param("courseReviewId") Long courseReviewId);
}

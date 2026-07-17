package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseReviewImageRepository extends JpaRepository<CourseReviewImage, Long> {

    List<CourseReviewImage> findAllByCourseReview_IdOrderByImageOrderAsc(Long courseReviewId);

    List<CourseReviewImage> findAllByCourseReview_IdInOrderByCourseReview_IdAscImageOrderAsc(
            List<Long> courseReviewIds
    );
}

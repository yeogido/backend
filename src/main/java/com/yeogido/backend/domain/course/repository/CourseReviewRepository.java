package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {
}

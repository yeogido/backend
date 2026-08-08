package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseItemTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseItemTimeRepository extends JpaRepository<CourseItemTime, Long> {
}

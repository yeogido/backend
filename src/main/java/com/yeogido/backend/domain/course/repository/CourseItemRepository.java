package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {
}

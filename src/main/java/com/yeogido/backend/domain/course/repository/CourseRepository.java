package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"user", "region"})
    Optional<Course> findCourseDetailByIdAndDeletedAtIsNull(Long id);
}

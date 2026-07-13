package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseHashtagRepository extends JpaRepository<CourseHashtag, Long> {
}

package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.Course;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseViewCountSyncRepository extends JpaRepository<Course, Long> {

    @Modifying
    @Transactional
    @Query("""
            update Course c
               set c.viewCount = c.viewCount + :delta
             where c.id = :courseId
               and c.deletedAt is null
            """)
    int increaseViewCount(@Param("courseId") Long courseId, @Param("delta") long delta);
}

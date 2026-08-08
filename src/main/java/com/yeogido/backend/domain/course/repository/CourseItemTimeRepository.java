package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseItemTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseItemTimeRepository extends JpaRepository<CourseItemTime, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from CourseItemTime cit
            where cit.fromCourseItem.course.id = :courseId
               or cit.toCourseItem.course.id = :courseId
            """)
    void deleteAllByCourseId(@Param("courseId") Long courseId);
}

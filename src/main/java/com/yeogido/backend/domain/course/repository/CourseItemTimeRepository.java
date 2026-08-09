package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseItemTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseItemTimeRepository extends JpaRepository<CourseItemTime, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from CourseItemTime cit
            where cit.fromCourseItem.course.id = :courseId
               or cit.toCourseItem.course.id = :courseId
            """)
    void deleteAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            select cit
            from CourseItemTime cit
            join fetch cit.fromCourseItem fromItem
            join fetch cit.toCourseItem toItem
            where fromItem.course.id = :courseId
               or toItem.course.id = :courseId
            """)
    List<CourseItemTime> findAllByCourseId(@Param("courseId") Long courseId);
}

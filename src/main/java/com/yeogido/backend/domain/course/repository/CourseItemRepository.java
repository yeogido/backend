package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {

    @Modifying
    @Query("delete from CourseItem ci where ci.course.id = :courseId")
    void deleteAllByCourseId(@Param("courseId") Long courseId);
}

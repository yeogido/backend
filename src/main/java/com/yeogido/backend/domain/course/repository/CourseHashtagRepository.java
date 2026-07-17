package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseHashtagRepository extends JpaRepository<CourseHashtag, Long> {

    @Modifying
    @Query("delete from CourseHashtag ch where ch.course.id = :courseId")
    void deleteAllByCourseId(@Param("courseId") Long courseId);
}

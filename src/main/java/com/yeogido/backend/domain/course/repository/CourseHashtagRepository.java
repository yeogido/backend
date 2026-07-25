package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.CourseHashtag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseHashtagRepository extends JpaRepository<CourseHashtag, Long> {

    @EntityGraph(attributePaths = "hashtag")
    List<CourseHashtag> findByCourseId(Long courseId);

    @Query("""
            select ch
            from CourseHashtag ch
            join fetch ch.hashtag
            where ch.course.id in :courseIds
            """)
    List<CourseHashtag> findByCourseIdIn(@Param("courseIds") List<Long> courseIds);

    @Modifying
    @Query("delete from CourseHashtag ch where ch.course.id = :courseId")
    void deleteAllByCourseId(@Param("courseId") Long courseId);

    List<CourseHashtag> findByCourseIdIn(List<Long> courseIds);
}

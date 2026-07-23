package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"user", "region"})
    Optional<Course> findCourseDetailByIdAndDeletedAtIsNull(Long id);

    @Query("""
            select
                c.id as courseId,
                c.title as title,
                c.thumbnailKey as thumbnailKey,
                c.durationType as durationType,
                c.transportType as transportType,
                c.companionType as companionType
            from Course c
            where c.id = :courseId
              and c.deletedAt is null
            """)
    Optional<CourseSummaryProjection> findSummaryByCourseId(@Param("courseId") Long courseId);

    @Query("""
            select
                c.id as courseId,
                c.courseType as courseType,
                c.region.id as regionId
            from Course c
            where c.id in :courseIds
              and c.deletedAt is null
            """)
    List<CourseRankingProjection> findRankingTargetsByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    interface CourseSummaryProjection {

        Long getCourseId();

        String getTitle();

        String getThumbnailKey();

        DurationType getDurationType();

        TransportType getTransportType();

        CompanionType getCompanionType();
    }

    interface CourseRankingProjection {

        Long getCourseId();

        CourseType getCourseType();

        Long getRegionId();
    }
}

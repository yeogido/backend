package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long>, CourseQueryRepository {

    Optional<Course> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

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
                c.region.id as regionId,
                c.region.parent.id as parentRegionId,
                c.createdAt as createdAt
            from Course c
            where c.id in :courseIds
              and c.deletedAt is null
            """)
    List<CourseRankingProjection> findRankingTargetsByCourseIds(
            @Param("courseIds") Collection<Long> courseIds
    );

    @Query("""
            select
                c.id as courseId,
                c.thumbnailKey as thumbnailKey,
                c.title as title,
                c.region.name as region,
                c.durationType as durationType,
                c.transportType as transportType,
                c.companionType as companionType
            from Course c
            where c.id in :courseIds
              and c.deletedAt is null
            """)
    List<CoursePopularProjection> findPopularCoursesByCourseIds(
            @Param("courseIds") Collection<Long> courseIds
    );

    @Query("""
            select
                c.id as courseId,
                c.title as title,
                c.description as description,
                c.thumbnailKey as thumbnailKey,
                c.durationType as durationType,
                c.transportType as transportType
            from Course c
            where c.courseType = com.yeogido.backend.domain.course.enums.CourseType.OFFICIAL
              and c.recommendOrder is not null
              and c.deletedAt is null
            order by c.recommendOrder asc
            """)
    List<CourseRecommendedProjection> findRecommendedCourses(Pageable pageable);

    @Query("""
            select c.id
            from Course c
            where c.courseType = com.yeogido.backend.domain.course.enums.CourseType.OFFICIAL
              and c.deletedAt is null
            order by c.createdAt desc
            """)
    List<Long> findLatestOfficialCourseIds(Pageable pageable);

    @Query("""
            select c.id
            from Course c
            where c.courseType = com.yeogido.backend.domain.course.enums.CourseType.OFFICIAL
              and (
                  c.region.id = :regionId
                  or c.region.parent.id = :regionId
              )
              and c.deletedAt is null
            order by c.createdAt desc
            """)
    List<Long> findLatestOfficialRegionCourseIds(
            @Param("regionId") Long regionId,
            Pageable pageable
    );

    @Query("""
            select c.id
            from Course c
            where c.courseType = com.yeogido.backend.domain.course.enums.CourseType.LOCAL
              and c.deletedAt is null
            order by c.createdAt desc
            """)
    List<Long> findLatestLocalCourseIds(Pageable pageable);

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

        Long getParentRegionId();

        LocalDateTime getCreatedAt();
    }

    interface CoursePopularProjection {

        Long getCourseId();

        String getThumbnailKey();

        String getTitle();

        String getRegion();

        DurationType getDurationType();

        TransportType getTransportType();

        CompanionType getCompanionType();
    }

    interface CourseRecommendedProjection {

        Long getCourseId();

        String getTitle();

        String getDescription();

        String getThumbnailKey();

        DurationType getDurationType();

        TransportType getTransportType();
    }
}

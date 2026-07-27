package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseLikeRepository extends JpaRepository<CourseLike, Long> {

    boolean existsByUserIdAndCourseId(Long memberId, Long courseId);

    Optional<CourseLike> findByUserIdAndCourseId(Long memberId, Long courseId);

    long countByCourseId(Long courseId);

    boolean existsByCourseAndUser(Course course, User user);

    @Query("""
            select cl.course.id
            from CourseLike cl
            where cl.user.id = :userId
              and cl.course.id in :courseIds
            """)
    List<Long> findLikedCourseIdsByUserIdAndCourseIdIn(
            @Param("userId") Long userId,
            @Param("courseIds") List<Long> courseIds
    );


    @Query("""
        select cl.course.id
        from CourseLike cl
        where cl.user = :user
        and cl.course.id in :courseIds
    """)
    List<Long> findLikedCourseIds(
            @Param("user") User user,
            @Param("courseIds") List<Long> courseIds
    );
}

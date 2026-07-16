package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseLikeRepository extends JpaRepository<CourseLike, Long> {

    boolean existsByUserIdAndCourseId(Long memberId, Long courseId);

    Optional<CourseLike> findByUserIdAndCourseId(Long memberId, Long courseId);

    long countByCourseId(Long courseId);

    boolean existsByCourseAndUser(Course course, User user);
}

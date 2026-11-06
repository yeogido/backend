package com.yeogido.backend.domain.friend.repository;

import com.yeogido.backend.domain.course.entity.Course;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface FriendCourseRepository extends Repository<Course, Long> {

    // 친구가 작성한 우리동네 코스를 최신순으로 조회
    @Query("""
        select course
        from Course course
        join fetch course.region
        where course.user.id = :friendUserId
          and course.courseType = com.yeogido.backend.domain.course.enums.CourseType.LOCAL
          and course.deletedAt is null
          and (:cursorId is null or course.id < :cursorId)
        order by course.id desc
        """)
    List<Course> findLocalCourses(
            @Param("friendUserId") Long friendUserId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}

package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.util.List;

public interface CourseService {

    CourseResDTO.CourseIdRes createCourse(Long userId, CourseReqDTO.CourseCreateReq request);

    CourseResDTO.CourseIdRes updateCourse(Long userId, Long courseId, CourseReqDTO.CourseUpdateReq request);

    void deleteCourse(Long userId, Long courseId);

    CursorResponse<CourseResDTO.CoursePreview> getCourses(CourseReqDTO.CourseListReq request);

    List<CourseResDTO.CoursePreview> getPopularCourses(CourseReqDTO.CoursePopularReq request);

    CourseResDTO.CourseDetail getCourseDetail(Long courseId);

    CourseResDTO.CourseSummary getCourseSummary(Long courseId, Long userId);

    CourseResDTO.ReviewCreateRes createCourseReview(Long courseId, CourseReqDTO.ReviewCreateReq request);

    CourseResDTO.CourseLikeRes createCourseLike(Long courseId);

    CourseResDTO.CourseLikeRes deleteCourseLike(Long courseId);
}

package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.util.List;

public interface CourseService {

    CursorResponse<CourseResDTO.CoursePreview> getCourses(CourseReqDTO.CourseListReq request);

    List<CourseResDTO.CoursePreview> getPopularCourses(CourseReqDTO.CoursePopularReq request);

    CourseResDTO.CourseDetail getCourse(Long courseId);
}

package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface CourseService {

    CursorResponse<CourseResDTO.CoursePreview> getCourses(CourseReqDTO.CourseListReq request);
}

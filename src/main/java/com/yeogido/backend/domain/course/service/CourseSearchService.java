package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;

public interface CourseSearchService {

  Interpretation interpret(CourseListReq request);
}

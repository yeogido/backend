package com.yeogido.backend.domain.course.controller;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.service.CourseService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "추천 코스 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "추천 코스 목록 조회", description = "추천 코스 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<CursorResponse<CourseResDTO.CoursePreview>> getCourses(
            @Valid @ParameterObject @ModelAttribute CourseReqDTO.CourseListReq request
    ) {
        CursorResponse<CourseResDTO.CoursePreview> response = courseService.getCourses(request);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

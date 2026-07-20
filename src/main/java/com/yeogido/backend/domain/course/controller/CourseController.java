package com.yeogido.backend.domain.course.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.service.CourseService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.swagger.SwaggerExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Course", description = "추천 코스 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            name = "추천 코스 등록 예시",
                            value = SwaggerExamples.COURSE_CREATE
                    )
            )
    )
    @Operation(summary = "추천 코스 등록", description = "추천 코스를 등록합니다.")
    @PostMapping
    public ApiResponse<CourseResDTO.CourseIdRes> createCourse(
            @Valid @RequestBody CourseReqDTO.CourseCreateReq request
    ) {
        CourseResDTO.CourseIdRes response = courseService.createCourse(request);

        return ApiResponse.onSuccess(SuccessCode.CREATED, response);
    }

    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            name = "추천 코스 수정 예시",
                            value = SwaggerExamples.COURSE_UPDATE
                    )
            )
    )
    @Operation(summary = "추천 코스 수정", description = "추천 코스를 수정합니다.")
    @PatchMapping("/{courseId}")
    public ApiResponse<CourseResDTO.CourseIdRes> updateCourse(
            @Parameter(description = "코스 ID", example = "15")
            @PathVariable Long courseId,
            @Valid @RequestBody CourseReqDTO.CourseUpdateReq request
    ) {
        CourseResDTO.CourseIdRes response = courseService.updateCourse(courseId, request);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "추천 코스 삭제", description = "추천 코스를 삭제합니다.")
    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> deleteCourse(
            @Parameter(description = "코스 ID", example = "15")
            @PathVariable Long courseId
    ) {
        courseService.deleteCourse(courseId);

        return ApiResponse.onSuccess(SuccessCode.OK);
    }

    @Operation(summary = "추천 코스 목록 조회", description = "추천 코스 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<CursorResponse<CourseResDTO.CoursePreview>> getCourses(
            @Valid @ParameterObject @ModelAttribute CourseReqDTO.CourseListReq request
    ) {
        CursorResponse<CourseResDTO.CoursePreview> response = courseService.getCourses(request);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "인기 추천 코스 미리보기 조회", description = "추천 코스 홈 화면에 노출되는 인기 추천 코스 미리보기를 조회합니다.")
    @GetMapping("/popular")
    public ApiResponse<List<CourseResDTO.CoursePreview>> getPopularCourses(
            @Valid @ParameterObject @ModelAttribute CourseReqDTO.CoursePopularReq request
    ) {
        List<CourseResDTO.CoursePreview> response = courseService.getPopularCourses(request);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "추천 코스 상세 조회", description = "추천 코스 상세 정보를 조회합니다.")
    @GetMapping("/{courseId}")
    public ApiResponse<CourseResDTO.CourseDetail> getCourse(
            @Parameter(description = "코스 ID", example = "1")
            @PathVariable Long courseId
    ) {
        CourseResDTO.CourseDetail response = courseService.getCourse(courseId);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "추천 코스 요약 조회", description = "리뷰 작성 화면에서 사용하는 추천 코스 요약 정보를 조회합니다.")
    @GetMapping("/{courseId}/summary")
    public ApiResponse<CourseResDTO.CourseSummary> getCourseSummary(
            @Parameter(description = "코스 ID", example = "1")
            @PathVariable Long courseId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CourseResDTO.CourseSummary response = courseService.getCourseSummary(courseId, authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "추천 코스 리뷰 작성", description = "추천 코스 리뷰를 작성합니다.")
    @PostMapping("/{courseId}/reviews")
    public ApiResponse<CourseResDTO.ReviewCreateRes> createCourseReview(
            @Parameter(description = "코스 ID", example = "1")
            @PathVariable Long courseId,
            @Valid @RequestBody CourseReqDTO.ReviewCreateReq request
    ) {
        CourseResDTO.ReviewCreateRes response = courseService.createCourseReview(courseId, request);

        return ApiResponse.onSuccess(SuccessCode.CREATED, response);
    }

    @Operation(summary = "추천 코스 좋아요 등록", description = "추천 코스에 좋아요를 등록합니다.")
    @PostMapping("/{courseId}/likes")
    public ApiResponse<CourseResDTO.CourseLikeRes> createCourseLike(
            @Parameter(description = "코스 ID", example = "1")
            @PathVariable Long courseId
    ) {
        CourseResDTO.CourseLikeRes response = courseService.createCourseLike(courseId);

        return ApiResponse.onSuccess(SuccessCode.CREATED, response);
    }

    @Operation(summary = "추천 코스 좋아요 취소", description = "추천 코스 좋아요를 취소합니다.")
    @DeleteMapping("/{courseId}/likes")
    public ApiResponse<CourseResDTO.CourseLikeRes> deleteCourseLike(
            @Parameter(description = "코스 ID", example = "1")
            @PathVariable Long courseId
    ) {
        CourseResDTO.CourseLikeRes response = courseService.deleteCourseLike(courseId);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

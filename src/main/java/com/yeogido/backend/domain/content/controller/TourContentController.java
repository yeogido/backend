package com.yeogido.backend.domain.content.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.content.dto.TourContentDTO;
import com.yeogido.backend.domain.content.dto.TourContentSyncDTO;
import com.yeogido.backend.domain.content.service.TourContentService;
import com.yeogido.backend.domain.content.service.TourContentSyncService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tour Content", description = "관리자용 한국관광공사 문화콘텐츠 연동 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tour-contents")
public class TourContentController {

    private final TourContentService tourContentService;
    private final TourContentSyncService tourContentSyncService;

    @Operation(summary = "한국관광공사 행사 검색")
    @GetMapping
    public ApiResponse<TourContentDTO.SearchResponse> search(
            @Valid @ParameterObject @ModelAttribute TourContentDTO.SearchRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                tourContentService.search(request, authUser.userId())
        );
    }

    @Operation(summary = "한국관광공사 행사 상세·이미지 조회")
    @GetMapping("/{contentId}")
    public ApiResponse<TourContentDTO.DetailResponse> getDetail(
            @PathVariable String contentId,
            @RequestParam(defaultValue = "15") String contentTypeId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                tourContentService.getDetail(contentId, contentTypeId, authUser.userId())
        );
    }

    @Operation(summary = "한국관광공사 이미지를 S3에 저장")
    @PostMapping("/images/import")
    public ApiResponse<TourContentDTO.ImageImportResponse> importImage(
            @Valid @RequestBody TourContentDTO.ImageImportRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                tourContentService.importImage(request, authUser.userId())
        );
    }

    @Operation(summary = "한국관광공사 행사 데이터 즉시 동기화")
    @PostMapping("/sync")
    public ApiResponse<TourContentSyncDTO.Result> synchronize(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                tourContentSyncService.synchronize(authUser.userId())
        );
    }
}

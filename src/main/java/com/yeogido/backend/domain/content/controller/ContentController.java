package com.yeogido.backend.domain.content.controller;


import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.service.ContentService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.yeogido.backend.domain.auth.security.AuthUser;

import java.util.List;

@Tag(
        name = "Content",
        description = "문화콘텐츠 API"
)
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/contents")
public class ContentController {

    private final ContentService contentService;

    //문화콘텐츠 목록 조회
    @Operation(
            summary = "문화콘텐츠 목록 조회",
            description = "문화콘텐츠를 검색어, 카테고리, 지역 및 조회 범위로 필터링하고 추천순, 저장순, 거리순 또는 종료 임박순으로 정렬합니다. "
    )
    @GetMapping
    public ApiResponse<CursorResponse<ContentResDTO.ContentInfo>> getContents(
            @ParameterObject @ModelAttribute ContentReqDTO.ContentListReq request,
            @AuthenticationPrincipal AuthUser authUser
    ){
        Long userId = authUser == null ? null : authUser.userId();
        CursorResponse<ContentResDTO.ContentInfo> result = contentService.getContents(request, userId);
        return ApiResponse.onSuccess(SuccessCode.OK,result);
    }


    //문화콘텐츠 상세 조회
    @Operation(
            summary = "문화콘텐츠 상세 조회",
            description = "문화콘텐츠 ID를 기반으로 문화콘텐츠의 상세 정보를 조회합니다."
    )
    @GetMapping("/{contentId}")
    public ApiResponse<ContentResDTO.ContentDetailRes> getContentDetail(
            @PathVariable Long contentId,
            @AuthenticationPrincipal AuthUser authUser
    ){
        Long userId = authUser != null ? authUser.userId() : null;

        ContentResDTO.ContentDetailRes result = contentService.getContentDetail(contentId,userId);
        return ApiResponse.onSuccess(SuccessCode.OK,result);

    }


    //문화콘텐츠 등록
    @Operation(
            summary = "문화콘텐츠 등록",
            description = "문화콘텐츠를 등록합니다."

    )
    @PostMapping
    public ApiResponse<ContentResDTO.ContentCreateRes> createContent(
            @RequestBody @Valid ContentReqDTO.ContentCreateReq request,
            @AuthenticationPrincipal AuthUser authUser

    ){
        ContentResDTO.ContentCreateRes result = contentService.createContent(request, authUser.userId());
        return ApiResponse.onSuccess(SuccessCode.CREATED, result);
    }


    //문화콘텐츠 수정
    @Operation(
            summary = "문화콘텐츠 수정",
            description = "문화콘텐츠 정보를 수정합니다."
    )
    @PatchMapping("/{contentId}")
    public ApiResponse<ContentResDTO.ContentUpdateRes> updateContent(
            @PathVariable Long contentId,
            @RequestBody @Valid ContentReqDTO.ContentUpdateReq request,
            @AuthenticationPrincipal AuthUser authUser
    ){
        ContentResDTO.ContentUpdateRes result = contentService.updateContent(contentId,request, authUser.userId());
        return ApiResponse.onSuccess(SuccessCode.OK,result);
    }

    // 문화콘텐츠 삭제
    @Operation(
            summary = "문화콘텐츠 삭제",
            description = "문화콘텐츠를 삭제합니다."
    )
    @DeleteMapping("/{contentId}")
    public ApiResponse<Void> deleteContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal AuthUser authUser
    ){
        contentService.deleteContent(contentId, authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }


    //문화콘텐츠 좋아요 등록
    @Operation(
            summary = "문화콘텐츠 좋아요 등록",
            description = "해당 문화콘텐츠에 좋아요를 등록합니다"
    )
    @PutMapping("/{contentId}/likes")
    public ApiResponse<ContentResDTO.ContentLikeRes> likeContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal AuthUser authUser
    ){

        ContentResDTO.ContentLikeRes result =
                contentService.likeContent(contentId, authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK, result);

    }


    //문화콘텐츠 좋아요 취소
    @Operation(
            summary = "문화콘텐츠 좋아요 취소",
            description = "해당 문화콘텐츠의 좋아요를 취소합니다."
    )
    @DeleteMapping("/{contentId}/likes")
    public ApiResponse<ContentResDTO.ContentLikeRes> unlikeContent(
            @PathVariable Long contentId,
            @AuthenticationPrincipal AuthUser authUser
    ) {

        ContentResDTO.ContentLikeRes result =
                contentService.unlikeContent(contentId, authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "여기도 추천 대표 행사 조회 API",
            description = "종료일이 가까운 순으로 대표 행사 목록을 조회합니다."
    )
    @GetMapping("/banner")
    public ApiResponse<List<ContentResDTO.BannerRes>> getBannerContents() {
        List<ContentResDTO.BannerRes> result = contentService.getBannerContents();
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "진행 중인 행사 조회",
            description = "홈 화면에 노출할 진행 중인 행사를 추천순으로 조회합니다."
    )
    @GetMapping("/ongoing")
    public ApiResponse<List<ContentResDTO.OngoingContentRes>> getOngoingContents(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        Long userId = authUser == null ? null : authUser.userId();
        List<ContentResDTO.OngoingContentRes> result = contentService.getOngoingContents(userId);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

}

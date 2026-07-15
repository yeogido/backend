package com.yeogido.backend.domain.content.controller;


import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.service.ContentService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.common.response.StringCursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/contents")
public class ContentController {

    private final ContentService contentService;

    //문화콘텐츠 목록 조회
    @Operation(
            summary = "문화콘텐츠 목록 조회",
            description = "검색어, 카테고리, 정렬 조건을 기준으로 문화콘텐츠 목록을 조회합니다."

    )
    @GetMapping
    public ApiResponse<StringCursorResponse<ContentResDTO.ContentInfo>> getContents(
            @ModelAttribute ContentReqDTO.ContentListReq request
    ){
        StringCursorResponse<ContentResDTO.ContentInfo> result = contentService.getContents(request);
        return ApiResponse.onSuccess(SuccessCode.OK,result);
    }


    //문화콘텐츠 상세 조회
    @Operation(
            summary = "문화콘텐츠 상세 조회",
            description = "문화콘텐츠 ID를 기반으로 문화콘텐츠의 상세 정보를 조회합니다."
    )
    @GetMapping("/{contentId}")
    public ApiResponse<ContentResDTO.ContentDetailRes> getContentDetail(
            @PathVariable Long contentId
    ){
        ContentResDTO.ContentDetailRes result = contentService.getContentDetail(contentId);
        return ApiResponse.onSuccess(SuccessCode.OK,result);

    }


    //문화콘텐츠 등록
    @Operation(
            summary = "문화콘텐츠 등록",
            description = "문화콘텐츠를 등록합니다."

    )
    @PostMapping
    public ApiResponse<ContentResDTO.ContentCreateRes> createContent(
            @RequestBody @Valid ContentReqDTO.ContentCreateReq request

    ){
        ContentResDTO.ContentCreateRes result = contentService.createContent(request);
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
            @RequestBody @Valid ContentReqDTO.ContentCreateReq request
    ){
        ContentResDTO.ContentUpdateRes result = contentService.updateContent(contentId,request);
        return ApiResponse.onSuccess(SuccessCode.OK,result);
    }


    //문화콘텐츠 좋아요 등록
    @Operation(
            summary = "문화콘텐츠 좋아요 등록",
            description = "해당 문화콘텐츠에 좋아요를 등록합니다"
    )
    @PostMapping("/{contentId}/likes")
    public ApiResponse<ContentResDTO.ContentLikeRes> likeContent(
            @PathVariable Long contentId
    ){
        ContentResDTO.ContentLikeRes result = contentService.likeContent(contentId);
        return ApiResponse.onSuccess(SuccessCode.OK,result);
    }


}

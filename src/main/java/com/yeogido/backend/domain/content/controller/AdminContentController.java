package com.yeogido.backend.domain.content.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.service.ContentService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Admin Content",
        description = "관리자용 문화콘텐츠 승인 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/contents")
public class AdminContentController {

    private final ContentService contentService;

    @Operation(
            summary = "관광공사 콘텐츠 게시 대기 목록 조회",
            description = "DB에 동기화되었지만 아직 게시되지 않은 관광공사 콘텐츠를 조회합니다. 관리자만 접근할 수 있습니다."
    )
    @GetMapping("/pending")
    public ApiResponse<List<ContentResDTO.PendingContentRes>>
    getPendingTourContents(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<ContentResDTO.PendingContentRes> result =
                contentService.getPendingTourContents(authUser.userId());
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "관광공사 콘텐츠 승인·게시",
            description = "동기화된 콘텐츠를 보완한 후 게시합니다. 새로운 콘텐츠 행을 생성하지 않습니다. 관리자만 접근할 수 있습니다."
    )
    @PatchMapping("/{contentId}/publish")
    public ApiResponse<ContentResDTO.ContentUpdateRes> publishTourContent(
            @PathVariable Long contentId,
            @RequestBody @Valid ContentReqDTO.ContentPublishReq request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        ContentResDTO.ContentUpdateRes result =
                contentService.publishTourContent(
                        contentId,
                        request,
                        authUser.userId()
                );
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }
}

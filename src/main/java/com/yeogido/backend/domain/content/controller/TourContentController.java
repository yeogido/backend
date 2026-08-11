package com.yeogido.backend.domain.content.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.content.dto.TourContentSyncDTO;
import com.yeogido.backend.domain.content.service.TourContentSyncService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tour Content", description = "관리자용 한국관광공사 문화콘텐츠 연동 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tour-contents")
public class TourContentController {

    private final TourContentSyncService tourContentSyncService;

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

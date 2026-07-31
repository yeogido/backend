package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;
import com.yeogido.backend.domain.travel.service.StickerService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sticker", description = "여행 기록 스티커 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stickers")
public class StickerController {

    private final StickerService stickerService;

    @Operation(
            summary = "여행 기록 스티커 목록 조회",
            description = "여행 기록 폴더 꾸미기에 사용할 기본 제공 스티커와 로그인 사용자의 커스텀 스티커 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<StickerResDTO.StickerListResponse> getStickers(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        StickerResDTO.StickerListResponse result = stickerService.getStickers(authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }
}

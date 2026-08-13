package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.travel.dto.request.StickerReqDTO;
import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;
import com.yeogido.backend.domain.travel.service.StickerService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(
            summary = "커스텀 스티커 등록",
            description = "사용자가 temp 경로에 업로드한 이미지를 최종 스티커 경로로 이동한 뒤 커스텀 스티커로 등록합니다."
    )
    @PostMapping
    public ApiResponse<StickerResDTO.CreateResponse> createCustomSticker(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody StickerReqDTO.CreateRequest request
    ) {
        StickerResDTO.CreateResponse result = stickerService.createCustomSticker(
                authUser.userId(),
                request
        );

        return ApiResponse.onSuccess(SuccessCode.CREATED, result);
    }

    @Operation(
            summary = "커스텀 스티커 삭제",
            description = "로그인한 사용자가 본인이 등록한 커스텀 스티커를 삭제합니다. 기존 여행 기록에 배치된 스티커는 유지됩니다."
    )
    @DeleteMapping("/{stickerId}")
    public ApiResponse<Void> deleteCustomSticker(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long stickerId
    ) {
        stickerService.deleteCustomSticker(authUser.userId(), stickerId);

        return ApiResponse.onSuccess(SuccessCode.OK);
    }
}

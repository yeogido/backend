package com.yeogido.backend.domain.place.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Place", description = "장소 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    @Operation(
            summary = "장소 좋아요 등록",
            description = "사용자가 특정 장소에 좋아요를 등록합니다."
    )
    @PostMapping("/{placeId}/likes")
    public ApiResponse<Void> createPlaceLike(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId
    ) {
        placeService.createPlaceLike(authUser.userId(), placeId);

        return ApiResponse.onSuccess(SuccessCode.CREATED);
    }

    @Operation(
            summary = "장소 좋아요 취소",
            description = "사용자가 특정 장소에 등록한 좋아요를 취소합니다"
    )
    @DeleteMapping("/{placeId}/likes")
    public ApiResponse<Void> deletePlaceLike(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId
    ) {
        placeService.deletePlaceLike(authUser.userId(), placeId);

        return ApiResponse.onSuccess(SuccessCode.OK);
    }
}

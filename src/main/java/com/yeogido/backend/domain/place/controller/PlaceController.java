package com.yeogido.backend.domain.place.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.place.dto.request.PlaceLikeRequest;
import com.yeogido.backend.domain.place.dto.response.PlaceResponse;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
            description = "사용자가 특정 장소에 좋아요를 등록합니다. 이미 좋아요한 장소인 경우에도 기존 좋아요 상태를 반환합니다."
    )
    @PutMapping("/{placeId}/likes")
    public ApiResponse<PlaceResponse.PlaceLikeRes> createPlaceLike(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceLikeRequest request
    ) {
        PlaceResponse.PlaceLikeRes response =
                placeService.createPlaceLike(
                        authUser.userId(),
                        placeId,
                        request
                );

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "장소 좋아요 취소",
            description = "사용자가 특정 장소에 등록한 좋아요를 취소합니다. 좋아요가 없는 경우에도 정상적으로 해제 상태를 반환합니다."
    )
    @DeleteMapping("/{placeId}/likes")
    public ApiResponse<PlaceResponse.PlaceLikeRes> deletePlaceLike(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId
    ) {
        PlaceResponse.PlaceLikeRes response =
                placeService.deletePlaceLike(authUser.userId(), placeId);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

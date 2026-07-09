package com.yeogido.backend.domain.place.controller;

import com.yeogido.backend.domain.place.dto.response.PlaceResponse;
import com.yeogido.backend.global.common.ApiResponse;
import com.yeogido.backend.global.common.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Place", description = "장소 관련 API")
@RestController
@RequestMapping("/api/v1/places")
public class PlaceLikeController {

    @Operation(
            summary = "장소 좋아요 등록",
            description = "사용자가 특정 장소에 좋아요를 등록합니다."
    )
    @PostMapping("/{placeId}/likes")
    public ApiResponse<PlaceResponse.LikeCreate> createPlaceLike(
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId
    ) {
        PlaceResponse.LikeCreate response = PlaceResponse.LikeCreate.builder()
                .placeId(placeId)
                .isLiked(true)
                .build();
        return ApiResponse.onSuccess(SuccessCode.CREATED, response);
    }

    @Operation(
            summary = "장소 좋아요 취소",
            description = "사용자가 특정 장소에 등록한 좋아요를 취소합니다"
    )
    @DeleteMapping("/{placeId}/likes")
    public ApiResponse<PlaceResponse.LikeDelete> deletePlaceLike(
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId
    ) {
        PlaceResponse.LikeDelete response = PlaceResponse.LikeDelete.builder()
                .placeId(placeId)
                .isLiked(false)
                .build();
        return ApiResponse.onSuccess(SuccessCode.NO_CONTENT, response);
    }
}

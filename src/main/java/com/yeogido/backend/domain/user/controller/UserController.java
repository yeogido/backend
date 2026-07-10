package com.yeogido.backend.domain.user.controller;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.service.UserService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내가 좋아요한 항목 목록 조회",
            description = "사용자가 좋아요한 문화 콘텐츠 목록을 카테고리별로 조회합니다."
    )
    @GetMapping("/me/likes")
    public ApiResponse<CursorResponse<UserResDTO.LikedResponse>> getLikedList(
            @RequestParam String category,
            @RequestParam Long cursor,
            @RequestParam Integer size
    ) {
        CursorResponse<UserResDTO.LikedResponse> result = userService.getLikedList(category, cursor, size);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(summary = "내 프로필 조회 API", description = "로그인한 사용자의 프로필 및 계정 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserResDTO.Profile> getMyPage(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        UserResDTO.Profile response = userService.getMyPage(authorization);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

package com.yeogido.backend.domain.user.controller;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.service.UserService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.domain.auth.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "사용자 API", description = "사용자 관련 API")
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
            @RequestParam LikeCategory category,
            @RequestParam(required = false) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "6") Integer size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CursorResponse<UserResDTO.LikedResponse> result =
                userService.getLikedList(
                        authUser.userId(),
                        category,
                        cursorCreatedAt,
                        cursorId,
                        size
                );

        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(summary = "내 프로필 조회 API", description = "로그인한 사용자의 프로필 및 계정 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserResDTO.Profile> getMyPage(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserResDTO.Profile response = userService.getMyPage(authUser.userId());
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

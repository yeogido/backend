package com.yeogido.backend.domain.user.controller;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.service.UserService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    //사용자가 좋아요한 목록 조회
    @Operation(
            summary = "내가 좋아요한 항목 목록 조회",
            description = "사용자가 좋아요한 문화 콘텐츠 목록을 카테고리별로 조회합니다."

    )
    @GetMapping("/me/likes")
    public ApiResponse<CursorResponse<UserResDTO.LikedResponse>> getLikedList(
            @RequestParam String category,
            @RequestParam Long cursor,
            @RequestParam Integer size
    ){
        CursorResponse<UserResDTO.LikedResponse> result = userService.getLikedList(category, cursor, size);
        return ApiResponse.onSuccess(SuccessCode.OK,result);
    }
}

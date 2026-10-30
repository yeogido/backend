package com.yeogido.backend.domain.friend.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.service.FriendService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Friend", description = "친구 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "내 친구코드 조회 API", description = "프로필 QR 모달에 표시할 내 친구코드와 QR 딥링크를 조회합니다.")
    @GetMapping("/me/code")
    public ApiResponse<FriendResDTO.MyFriendCode> getMyFriendCode(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        FriendResDTO.MyFriendCode response = friendService.getMyFriendCode(authUser.userId());
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}

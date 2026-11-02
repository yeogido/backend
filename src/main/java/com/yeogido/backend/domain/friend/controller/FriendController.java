package com.yeogido.backend.domain.friend.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.friend.dto.FriendReqDTO;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.service.FriendService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "친구코드로 사용자 검색 API", description = "친구코드로 사용자를 검색하고 나와의 관계 상태를 함께 조회합니다.")
    @GetMapping("/search")
    public ApiResponse<FriendResDTO.SearchResult> searchByFriendCode(
            @RequestParam String friendCode,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        FriendResDTO.SearchResult response = friendService.searchByFriendCode(authUser.userId(), friendCode);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "친구 요청 보내기 API", description = "친구코드에 해당하는 사용자에게 친구 요청을 보냅니다.")
    @PostMapping("/requests")
    public ApiResponse<FriendResDTO.SentRequest> sendRequest(
            @Valid @RequestBody FriendReqDTO.SendRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        FriendResDTO.SentRequest response = friendService.sendRequest(authUser.userId(), request);
        return ApiResponse.onSuccess(SuccessCode.CREATED, response);
    }

    @Operation(summary = "받은 친구 요청 목록 조회 API", description = "대기 중인 받은 친구 요청을 최신 요청순으로 조회합니다.")
    @GetMapping("/requests/received")
    public ApiResponse<CursorResponse<FriendResDTO.ReceivedRequestItem>> getReceivedRequests(
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CursorResponse<FriendResDTO.ReceivedRequestItem> response =
                friendService.getReceivedRequests(authUser.userId(), cursorId, size);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "보낸 친구 요청 목록 조회 API", description = "상대가 아직 수락하지 않은 보낸 친구 요청을 최신 요청순으로 조회합니다.")
    @GetMapping("/requests/sent")
    public ApiResponse<CursorResponse<FriendResDTO.SentRequestItem>> getSentRequests(
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CursorResponse<FriendResDTO.SentRequestItem> response =
                friendService.getSentRequests(authUser.userId(), cursorId, size);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "보낸 친구 요청 취소 API", description = "내가 보낸 대기 중인 친구 요청을 취소합니다.")
    @DeleteMapping("/requests/{requestId}")
    public ApiResponse<Void> cancelRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        friendService.cancelRequest(authUser.userId(), requestId);
        return ApiResponse.onSuccess(SuccessCode.OK);
    }

    @Operation(summary = "친구 요청 수락 API", description = "받은 친구 요청을 수락하여 친구가 됩니다.")
    @PatchMapping("/requests/{requestId}/accept")
    public ApiResponse<FriendResDTO.AcceptResult> acceptRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        FriendResDTO.AcceptResult response = friendService.acceptRequest(authUser.userId(), requestId);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "친구 요청 거절 API", description = "받은 친구 요청을 거절합니다. (화면의 \"삭제\" 버튼) 보낸 사람에게는 알리지 않습니다.")
    @PatchMapping("/requests/{requestId}/reject")
    public ApiResponse<Void> rejectRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        friendService.rejectRequest(authUser.userId(), requestId);
        return ApiResponse.onSuccess(SuccessCode.OK);
    }
}

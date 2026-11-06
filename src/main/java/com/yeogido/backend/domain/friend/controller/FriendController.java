package com.yeogido.backend.domain.friend.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.friend.dto.FriendReqDTO;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.service.FriendService;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    @Operation(summary = "친구 목록 조회 API", description = "수락된 친구를 이름순으로 조회합니다. keyword로 내 친구 안에서 이름(부분 일치) 또는 친구코드(앞부분 일치)를 검색합니다.")
    @GetMapping
    public ApiResponse<CursorResponse<FriendResDTO.UserSummary>> getFriends(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursorNickname,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CursorResponse<FriendResDTO.UserSummary> response =
                friendService.getFriends(authUser.userId(), keyword, cursorNickname, cursorId, size);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "친구 프로필 조회 API", description = "친구의 기본 정보를 조회합니다.")
    @GetMapping("/{friendUserId}")
    public ApiResponse<FriendResDTO.FriendProfile> getFriendProfile(
            @PathVariable Long friendUserId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        FriendResDTO.FriendProfile response = friendService.getFriendProfile(authUser.userId(), friendUserId);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "친구의 우리동네 추천 코스 조회 API", description = "친구가 작성한 우리동네 추천 코스를 최신순으로 조회합니다.")
    @GetMapping("/{friendUserId}/courses/local")
    public ApiResponse<CursorResponse<CourseResDTO.CoursePreview>> getFriendLocalCourses(
            @PathVariable Long friendUserId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CursorResponse<CourseResDTO.CoursePreview> response =
                friendService.getFriendLocalCourses(authUser.userId(), friendUserId, cursorId, size);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "친구 삭제 API",
            description = "친구 관계를 삭제하고 함께한 공유 앨범을 정리합니다. 친구 관계가 없으면 FRIEND4043을 반환합니다."
    )
    @DeleteMapping("/{friendUserId}")
    public ApiResponse<Void> deleteFriend(
            @PathVariable Long friendUserId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        friendService.deleteFriend(authUser.userId(), friendUserId);
        return ApiResponse.onSuccess(SuccessCode.OK);
    }

    @Operation(summary = "친구 여행 지도 조회 API", description = "친구가 만든 PUBLIC 기록과 내가 참여 중인 FRIENDS 기록을 지도 항목으로 조회합니다. 그 외 기록은 내려주지 않습니다.")
    @GetMapping("/{friendUserId}/travel-map")
    public ApiResponse<CursorResponse<TravelRecordResDTO.MapItem>> getFriendTravelMap(
            @PathVariable Long friendUserId,
            @Valid @ParameterObject @ModelAttribute FriendReqDTO.TravelMapRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CursorResponse<TravelRecordResDTO.MapItem> response =
                friendService.getFriendTravelMap(authUser.userId(), friendUserId, request);
        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(summary = "내 친구코드 조회 API", description = "프로필 QR 모달에 표시할 내 친구코드를 조회합니다.")
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
